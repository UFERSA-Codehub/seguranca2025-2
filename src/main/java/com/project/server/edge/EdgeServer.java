package com.project.server.edge;

import com.project.messageBus.udp.UdpMessage;
import com.project.messageBus.tcp.TcpDataMessage;
import com.project.messageBus.MessageType;
import com.project.model.Alerta;
import com.project.model.DadosAmbientais;
import com.project.security.DebugConfig;
import com.project.security.KeyManager;
import com.project.security.SessionKeys;
import com.project.security.RSA;
import com.project.server.AbstractServer;
import com.project.server.config.EdgeConfig;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.*;
import java.util.Base64;

public class EdgeServer extends AbstractServer implements Runnable {
    private DatagramSocket socket;              // Socket UDP
    private GestorAutenticacao autenticacao;    // Gestor de autenticação
    private CacheDados cache;                   // Cache local de dados
    private Thread thread;                      // Thread de execução (UDP)
    private Thread threadTCP;                   // Thread de envio TCP
    private long totalMensagensRecebidas;       // Contador de mensagens
    private long totalMensagensValidas;         // Contador de mensagens válidas
    private long totalMensagensInvalidas;       // Contador de mensagens inválidas
    
    // ======= RSA Handshake =======
    private RSA rsa;                            // Instância RSA para handshake
    private Map<String, SessionKeys> sessoesHandshake; // sensorId -> SessionKeys temporárias
    private Map<String, String> senhasValidadas;       // sensorId -> senha validada no HELLO
    
    // ======= TCP / Datacenter Integration =======
    private ClienteTCP clienteTCP;              // Cliente TCP para Datacenter
    private String datacenterHost;              // Host do Datacenter
    private int datacenterPorta;                // Porta TCP do Datacenter
    private String edgeId;                      // ID único deste Edge Server
    private int intervaloEnvioBatch;            // Intervalo em ms para enviar batches
    private int tamanhoBatch;                   // Número de leituras por batch
    private long totalBatchesEnviados;          // Contador de batches enviados

    public EdgeServer(int porta) {
        this(porta, null, 0, "EDGE_" + System.currentTimeMillis(), 30000, 50, 1000);
    }
    
    public EdgeServer(EdgeConfig config) {
        this(config.getPorta(), null, 0, config.getEdgeId(), 
             config.getIntervaloEnvioBatch(), config.getTamanhoBatch(), config.getCapacidadeCache());
    }

    public EdgeServer(int porta, String datacenterHost, int datacenterPorta, 
                      String edgeId, int intervaloEnvioBatch, int tamanhoBatch) {
        this(porta, datacenterHost, datacenterPorta, edgeId, intervaloEnvioBatch, tamanhoBatch, 1000);
    }
    
    private EdgeServer(int porta, String datacenterHost, int datacenterPorta, 
                      String edgeId, int intervaloEnvioBatch, int tamanhoBatch, int capacidadeCache) {
        super("EdgeServer", porta);
        this.autenticacao = new GestorAutenticacao();
        this.cache = new CacheDados(capacidadeCache);
        this.totalMensagensRecebidas = 0;
        this.totalMensagensValidas = 0;
        this.totalMensagensInvalidas = 0;
        
        // Configuração RSA para handshake
        this.rsa = new RSA();
        this.rsa.gerarParDeChaves();
        this.sessoesHandshake = new HashMap<>();
        this.senhasValidadas = new HashMap<>();
        
        // Configuração TCP
        this.datacenterHost = datacenterHost;
        this.datacenterPorta = datacenterPorta;
        this.edgeId = edgeId;
        this.intervaloEnvioBatch = intervaloEnvioBatch;
        this.tamanhoBatch = tamanhoBatch;
        this.totalBatchesEnviados = 0;
        
        // Criar cliente TCP se configurado
        if (datacenterHost != null && datacenterPorta > 0) {
            this.clienteTCP = new ClienteTCP(datacenterHost, datacenterPorta, edgeId);
        }
    }
    
    @Override
    protected boolean registrarNoDiscovery() {
        return autoRegistrarNoDiscovery("EDGE");
    }
    
    @Override
    public void exibirStatus() {
        exibirEstatisticas();
    }

    public void iniciar() {
        if (executando) {
            System.err.println("[EdgeServer] Servidor já está em execução!");
            return;
        }

        try {
            socket = new DatagramSocket(porta);
            executando = true;
            thread = new Thread(this, "EdgeServer-UDP");
            thread.start();

            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║              EDGE SERVER - INICIADO                            ║");
            System.out.println("╠════════════════════════════════════════════════════════════════╣");
            System.out.printf("║  Edge ID: %-52s ║%n", edgeId);
            System.out.printf("║  Porta UDP: %-50d ║%n", porta);
            System.out.printf("║  Sensores autorizados: %-39d ║%n", autenticacao.getTotalSensoresAutorizados());
            
            if (clienteTCP != null) {
                System.out.println("╠════════════════════════════════════════════════════════════════╣");
                System.out.printf("║  Datacenter: %s:%-39d║%n", datacenterHost, datacenterPorta);
                System.out.printf("║  Intervalo de envio: %-37ds ║%n", intervaloEnvioBatch / 1000);
                System.out.printf("║  Tamanho do batch: %-40d ║%n", tamanhoBatch);
                
                // Iniciar thread TCP para envio de batches
                threadTCP = new Thread(() -> loopEnvioBatches(), "EdgeServer-TCP");
                threadTCP.start();
            }
            
            System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        } catch (Exception e) {
            System.err.println("[EdgeServer] Erro ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
            executando = false;
        }
    }

    private void loopEnvioBatches() {
        System.out.println("[EdgeServer-TCP] 🚀 Iniciando envio periódico de batches ao Datacenter...\n");
        
        // Tentar conectar ao Datacenter
        if (!clienteTCP.conectar()) {
            System.err.println("[EdgeServer-TCP] ❌ Falha ao conectar ao Datacenter - envio de batches desabilitado");
            return;
        }
        
        while (executando) {
            try {
                // Aguardar intervalo
                Thread.sleep(intervaloEnvioBatch);
                
                // Verificar se há dados no cache
                int totalLeituras = cache.getTotalLeituras();
                if (totalLeituras == 0) {
                    if (DebugConfig.DEBUG_MODE) {
                        System.out.println("[EdgeServer-TCP] ⏳ Nenhuma leitura no cache para enviar");
                    }
                    continue;
                }
                
                // Criar e enviar batch
                enviarBatchParaDatacenter();
                
            } catch (InterruptedException e) {
                if (executando) {
                    System.err.println("[EdgeServer-TCP] Thread interrompida");
                }
                break;
            } catch (Exception e) {
                System.err.println("[EdgeServer-TCP] ❌ Erro no loop de envio: " + e.getMessage());
                if (DebugConfig.DEBUG_MODE) {
                    e.printStackTrace();
                }
            }
        }
        
        // Desconectar ao encerrar
        if (clienteTCP != null) {
            clienteTCP.desconectar();
        }
        
        System.out.println("[EdgeServer-TCP] Loop de envio de batches encerrado.");
    }

    private void enviarBatchParaDatacenter() {
        try {
            // Verificar conexão
            if (!clienteTCP.isConectado()) {
                System.out.println("[EdgeServer-TCP] 🔄 Reconectando ao Datacenter...");
                if (!clienteTCP.reconectar()) {
                    System.err.println("[EdgeServer-TCP] ❌ Falha ao reconectar - batch não enviado");
                    return;
                }
            }
            
            // Obter sensores ativos
            Set<String> sensores = cache.getTodosSensores();
            if (sensores.isEmpty()) {
                return;
            }
            
            // Criar mensagem TCP com batch de leituras
            TcpDataMessage batchMsg = new TcpDataMessage();
            int leiturasAdicionadas = 0;
            
            for (String sensorId : sensores) {
                // Obter últimas leituras do sensor (até tamanhoBatch / número de sensores)
                int leiturasRestantes = tamanhoBatch - leiturasAdicionadas;
                if (leiturasRestantes <= 0) break;
                
                int maxPorSensor = Math.max(1, leiturasRestantes / Math.max(1, sensores.size()));
                List<DadosAmbientais> leituras = cache.obterUltimasLeituras(sensorId, maxPorSensor);
                
                for (DadosAmbientais leitura : leituras) {
                    batchMsg.adicionarLeitura(sensorId, leitura);
                    leiturasAdicionadas++;
                    
                    if (leiturasAdicionadas >= tamanhoBatch) {
                        break;
                    }
                }
            }
            
            // Enviar batch se houver dados
            if (leiturasAdicionadas > 0) {
                boolean sucesso = clienteTCP.enviarBatch(batchMsg);
                
                if (sucesso) {
                    totalBatchesEnviados++;
                    System.out.printf("[EdgeServer-TCP] ✅ Batch #%d enviado: %d leituras de %d sensores%n",
                        totalBatchesEnviados, leiturasAdicionadas, sensores.size());
                } else {
                    System.err.println("[EdgeServer-TCP] ❌ Falha ao enviar batch");
                }
            }
            
        } catch (Exception e) {
            System.err.println("[EdgeServer-TCP] ❌ Erro ao enviar batch: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
        }
    }

    public void parar() {
        executando = false;

        // Parar socket UDP
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        // Aguardar thread UDP
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Aguardar thread TCP
        if (threadTCP != null) {
            try {
                threadTCP.interrupt();
                threadTCP.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Desconectar cliente TCP
        if (clienteTCP != null) {
            clienteTCP.desconectar();
        }

        System.out.println("\n[EdgeServer] Servidor parado.");
        exibirEstatisticas();
    }

    @Override
    public void run() {
        System.out.println("[EdgeServer] 🚀 Aguardando mensagens dos sensores...\n");

        byte[] buffer = new byte[4096];

        while (executando) {
            try {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                totalMensagensRecebidas++;

                // Extrair apenas os bytes recebidos (não todo o buffer)
                byte[] dadosRecebidos = new byte[pacote.getLength()];
                System.arraycopy(pacote.getData(), 0, dadosRecebidos, 0, pacote.getLength());
                
                String enderecoOrigem = pacote.getAddress().getHostAddress() + ":" + pacote.getPort();

                processarMensagem(dadosRecebidos, enderecoOrigem);

            } catch (Exception e) {
                if (executando) {
                    System.err.println("[EdgeServer] Erro ao processar pacote: " + e.getMessage());
                    if (DebugConfig.DEBUG_MODE) {
                        e.printStackTrace();
                    }
                }
            }
        }

        System.out.println("[EdgeServer] Loop de recepção encerrado.");
    }

    private void processarMensagem(byte[] dados, String origem) {
        try {
            // Tentar deserializar como plaintext primeiro (para mensagens de handshake)
            String dadosString = new String(dados, java.nio.charset.StandardCharsets.UTF_8);
            UdpMessage mensagemPlain = UdpMessage.deserializeFromString(dadosString);
            
            // SENSOR_HELLO: não criptografado (passo 1 do handshake)
            if (mensagemPlain != null && mensagemPlain.getType() == MessageType.SENSOR_HELLO) {
                processarHello(mensagemPlain, origem);
                return;
            }
            
            // SENSOR_KEY_EXCHANGE: plaintext com SessionKeys criptografadas com RSA (passo 3 do handshake)
            if (mensagemPlain != null && mensagemPlain.getType() == MessageType.SENSOR_KEY_EXCHANGE) {
                processarKeyExchange(mensagemPlain, origem);
                return;
            }
            
            // Mensagens de dados: criptografadas com SessionKeys de handshake
            // Tentar decifrar com cada sessão ativa
            for (Map.Entry<String, SessionKeys> entry : sessoesHandshake.entrySet()) {
                try {
                    SessionKeys sessionKeys = entry.getValue();
                    UdpMessage mensagem = UdpMessage.decryptMessage(dados, sessionKeys);
                    
                    if (mensagem != null) {
                        processarMensagemComSessao(mensagem, origem, sessionKeys);
                        return;
                    }
                } catch (Exception e) {
                    // HMAC inválido ou chave errada - tentar próxima sessão
                    continue;
                }
            }
            
            // Nenhuma decodificação funcionou
            totalMensagensInvalidas++;
            System.err.println("[EdgeServer] ❌ Mensagem inválida de " + origem + " (HMAC/Decriptação falhou ou sensor não autenticado)");
            
        } catch (Exception e) {
            totalMensagensInvalidas++;
            System.err.println("[EdgeServer] ❌ Erro ao processar mensagem: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Processar SENSOR_HELLO (passo 1 do handshake)
     * Recebe: sensorId + senha (plaintext)
     * Valida credenciais e envia SENSOR_CHALLENGE com chave pública RSA
     */
    private void processarHello(UdpMessage mensagem, String origem) {
        String sensorId = mensagem.getSensorId();
        String senha = mensagem.getCredenciais();
        
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[EdgeServer] 🤝 HELLO recebido de: " + sensorId + " (" + origem + ")");
        }
        
        // Validar credenciais
        if (!autenticacao.validarCredenciais(sensorId, senha)) {
            totalMensagensInvalidas++;
            System.err.println("[EdgeServer] ❌ Credenciais inválidas: " + sensorId);
            return;
        }
        
        // Guardar senha validada no cache para uso posterior no KEY_EXCHANGE
        senhasValidadas.put(sensorId, senha);
        
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[EdgeServer] ✅ Credenciais validadas e senha armazenada: " + sensorId);
        }
        
        // Enviar SENSOR_CHALLENGE com chave pública RSA
        String publicKeyBase64 = Base64.getEncoder().encodeToString(rsa.getChavePublica().getEncoded());
        UdpMessage challenge = UdpMessage.createChallenge(edgeId, publicKeyBase64);
        
        enviarMensagemPlaintext(challenge, origem);
        
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[EdgeServer] 📤 CHALLENGE enviado para: " + sensorId);
        }
    }
    
    /**
     * Processar SENSOR_KEY_EXCHANGE (passo 3 do handshake)
     * Recebe: SessionKeys criptografadas com chave pública RSA
     * Decripta com chave privada, registra sessão e envia SENSOR_AUTH_SUCCESS com JWT
     */
    private void processarKeyExchange(UdpMessage mensagem, String origem) {
        String sensorId = mensagem.getSensorId();
        byte[] encryptedKeysBytes = mensagem.getEncryptedSessionKeys();
        
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[EdgeServer] 🔑 KEY_EXCHANGE recebido de: " + sensorId + " (" + origem + ")");
        }
        
        try {
            // Decifrar SessionKeys com chave privada RSA
            byte[] decryptedKeys = rsa.decifrar(encryptedKeysBytes);
            
            if (decryptedKeys == null) {
                System.err.println("[EdgeServer] ❌ Erro ao decifrar SessionKeys de: " + sensorId);
                totalMensagensInvalidas++;
                return;
            }
            
            String keysJson = new String(decryptedKeys, java.nio.charset.StandardCharsets.UTF_8);
            
            // Parse JSON: formato "aesKey||hmacKey"
            String[] parts = keysJson.split("\\|\\|");
            if (parts.length != 2) {
                System.err.println("[EdgeServer] ❌ Formato de SessionKeys inválido de: " + sensorId);
                totalMensagensInvalidas++;
                return;
            }
            
            byte[] aesKeyBytes = Base64.getDecoder().decode(parts[0]);
            byte[] hmacKeyBytes = Base64.getDecoder().decode(parts[1]);
            
            // Criar SecretKey a partir dos bytes
            javax.crypto.SecretKey aesKey = new javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES");
            javax.crypto.SecretKey hmacKey = new javax.crypto.spec.SecretKeySpec(hmacKeyBytes, "HmacSHA256");
            
            // Criar SessionKeys
            SessionKeys sessionKeys = new SessionKeys(
                sensorId,
                aesKey,
                hmacKey,
                System.currentTimeMillis(),
                System.currentTimeMillis() + (30 * 60 * 1000) // 30 min
            );
            
            // Registrar no KeyManager e no mapa local
            KeyManager.registrarSessaoExterna(sensorId, sessionKeys);
            sessoesHandshake.put(sensorId, sessionKeys);
            
            // Obter senha validada do cache (armazenada no HELLO)
            String senhaValidada = senhasValidadas.get(sensorId);
            
            if (senhaValidada == null) {
                System.err.println("[EdgeServer] ❌ Senha não encontrada no cache para: " + sensorId);
                System.err.println("[EdgeServer] ⚠️  Sensor deve enviar HELLO antes de KEY_EXCHANGE");
                totalMensagensInvalidas++;
                return;
            }
            
            // Gerar JWT com senha real validada no HELLO
            String jwt = autenticacao.registrarESensorEObterJWT(sensorId, senhaValidada);
            
            if (jwt == null) {
                System.err.println("[EdgeServer] ❌ Erro ao gerar JWT para: " + sensorId);
                totalMensagensInvalidas++;
                return;
            }
            
            // Enviar SENSOR_AUTH_SUCCESS com JWT (criptografado com SessionKeys)
            UdpMessage authSuccess = new UdpMessage(
                MessageType.SENSOR_AUTH_SUCCESS,
                edgeId,
                jwt,
                null
            );
            
            byte[] dadosCifrados = authSuccess.encrypt(sessionKeys);
            enviarMensagemBytes(dadosCifrados, origem);
            
            totalMensagensValidas++;
            System.out.println("[EdgeServer] ✅ Handshake completo com: " + sensorId + " (" + origem + ")");
            
        } catch (Exception e) {
            totalMensagensInvalidas++;
            System.err.println("[EdgeServer] ❌ Erro ao processar KEY_EXCHANGE: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Processar mensagens com SessionKeys de handshake (mensagens de dados após handshake)
     */
    private void processarMensagemComSessao(UdpMessage mensagem, String origem, SessionKeys keys) {
        try {
            String sensorId = mensagem.getSensorId();
            
            // Validar JWT
            boolean autenticado = autenticacao.autenticarComJWT(sensorId, mensagem.getCredenciais());
            
            if (!autenticado) {
                totalMensagensInvalidas++;
                System.err.println("[EdgeServer] 🚫 JWT inválido: " + sensorId + " de " + origem);
                return;
            }
            
            totalMensagensValidas++;
            
            if (mensagem.getType() == MessageType.SENSOR_REGISTER) {
                System.out.println("[EdgeServer] ✅ REGISTER: " + sensorId + " de " + origem);
            }
            
            cache.adicionarLeitura(sensorId, mensagem.getDados());
            analisarDados(sensorId, mensagem.getDados());
            
            if (DebugConfig.DEBUG_MODE) {
                System.out.printf("[EdgeServer] 📊 %s: Temp=%.1f°C, CO2=%.0f ppm, PM2.5=%.1f µg/m³%n",
                    sensorId,
                    mensagem.getDados().getTemperatura(),
                    mensagem.getDados().getCo2(),
                    mensagem.getDados().getPm25()
                );
            }
            
        } catch (Exception e) {
            totalMensagensInvalidas++;
            System.err.println("[EdgeServer] ❌ Erro ao processar mensagem: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
        }
    }
    
    
    /**
     * Enviar mensagem plaintext (para SENSOR_CHALLENGE)
     */
    private void enviarMensagemPlaintext(UdpMessage mensagem, String destino) {
        try {
            String[] parts = destino.split(":");
            if (parts.length != 2) {
                System.err.println("[EdgeServer] ❌ Formato de destino inválido: " + destino);
                return;
            }
            
            String ip = parts[0];
            int porta = Integer.parseInt(parts[1]);
            
            String mensagemStr = mensagem.serializeToString();
            byte[] dados = mensagemStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            DatagramPacket pacote = new DatagramPacket(
                dados,
                dados.length,
                java.net.InetAddress.getByName(ip),
                porta
            );
            
            socket.send(pacote);
            
        } catch (Exception e) {
            System.err.println("[EdgeServer] ❌ Erro ao enviar mensagem plaintext: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Enviar mensagem em bytes (já criptografada)
     */
    private void enviarMensagemBytes(byte[] dados, String destino) {
        try {
            String[] parts = destino.split(":");
            if (parts.length != 2) {
                System.err.println("[EdgeServer] ❌ Formato de destino inválido: " + destino);
                return;
            }
            
            String ip = parts[0];
            int porta = Integer.parseInt(parts[1]);
            
            DatagramPacket pacote = new DatagramPacket(
                dados,
                dados.length,
                java.net.InetAddress.getByName(ip),
                porta
            );
            
            socket.send(pacote);
            
        } catch (Exception e) {
            System.err.println("[EdgeServer] ❌ Erro ao enviar mensagem bytes: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
        }
    }

    private void analisarDados(String sensorId, DadosAmbientais dados) {
        try {
            List<Alerta> alertas = Alerta.analisar(dados);

            if (!alertas.isEmpty()) {
                System.out.println("[EdgeServer] 🚨 ALERTAS detectados para " + sensorId + ":");
                for (Alerta alerta : alertas) {
                    System.out.println("   • [" + alerta.getNivelAlerta() + "] " + alerta.getMensagem());
                }
            }

        } catch (Exception e) {
            System.err.println("[EdgeServer] Erro ao analisar dados: " + e.getMessage());
        }
    }
    
    /**
     * Limpar sessão de um sensor (remove senha, SessionKeys e JWT)
     * Chamado quando sensor desconecta ou sessão expira
     */
    private void limparSessaoSensor(String sensorId) {
        senhasValidadas.remove(sensorId);
        sessoesHandshake.remove(sensorId);
        KeyManager.removerChavesDaSessao(sensorId);
        autenticacao.revogarToken(sensorId);
        
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[EdgeServer] 🧹 Sessão limpa para: " + sensorId);
        }
    }

    public void exibirEstatisticas() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  ESTATÍSTICAS DO EDGE SERVER                   ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Total de mensagens recebidas: %-30d ║%n", totalMensagensRecebidas);
        System.out.printf("║  Mensagens válidas: %-42d ║%n", totalMensagensValidas);
        System.out.printf("║  Mensagens inválidas: %-40d ║%n", totalMensagensInvalidas);
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Total de sensores: %-42d ║%n", cache.getTodosSensores().size());
        System.out.printf("║  Sensores ativos: %-44d ║%n", cache.obterSensoresAtivos().size());
        System.out.printf("║  Total de leituras armazenadas: %-29d ║%n", cache.getTotalLeituras());
        
        // Estatísticas TCP (se habilitado)
        if (clienteTCP != null) {
            System.out.println("╠════════════════════════════════════════════════════════════════╣");
            System.out.printf("║  Batches enviados ao Datacenter: %-27d ║%n", totalBatchesEnviados);
            String statusConexao = (clienteTCP.isConectado() ? "🟢 Conectado" : "🔴 Desconectado");
            System.out.printf("║  Status conexão TCP: %-39s ║%n", statusConexao);
        }
        
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Sensores Registrados:                                         ║");

        Set<String> sensores = cache.getTodosSensores();
        if (sensores.isEmpty()) {
            System.out.println("║    (nenhum sensor registrado)                                  ║");
        } else {
            for (String sensorId : sensores) {
                int leituras = cache.getTotalLeiturasDoSensor(sensorId);
                DadosAmbientais ultima = cache.obterUltimaLeitura(sensorId);
                String status = cache.obterSensoresAtivos().contains(sensorId) ? "🟢" : "🔴";
                
                System.out.printf("║  %s %-12s - %4d leituras", status, sensorId, leituras);
                
                if (ultima != null) {
                    System.out.printf(" (última: %.1f°C)             ║%n", ultima.getTemperatura());
                } else {
                    System.out.println("                          ║");
                }
            }
        }

        System.out.println("╚════════════════════════════════════════════════════════════════╝");
    }

    // Métodos herdados de AbstractServer:
    // - isExecutando() já está disponível
    // - getNome() já está disponível
    // - getPorta() já está disponível

    public CacheDados getCache() {
        return cache;
    }

    public long getTotalMensagensRecebidas() {
        return totalMensagensRecebidas;
    }

    public long getTotalMensagensValidas() {
        return totalMensagensValidas;
    }

    public static void main(String[] args) {
        int porta = 5000;

        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida. Usando porta padrão: 5000");
            }
        }

        EdgeServer servidor = new EdgeServer(porta);
        servidor.iniciar();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[EdgeServer] Recebido sinal de shutdown...");
            servidor.parar();
        }));

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            servidor.parar();
        }
    }
}
