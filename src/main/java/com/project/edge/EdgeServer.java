package com.project.edge;

import com.project.messageBus.udp.UdpMessage;
import com.project.messageBus.tcp.TcpDataMessage;
import com.project.messageBus.MessageType;
import com.project.model.Alerta;
import com.project.model.DadosAmbientais;
import com.project.security.DebugConfig;
import com.project.security.KeyManager;
import com.project.security.SessionKeys;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.*;

public class EdgeServer implements Runnable {
    private int porta;                          // Porta UDP para receber dados dos sensores
    private DatagramSocket socket;              // Socket UDP
    private boolean executando;                 // Flag de controle
    private GestorAutenticacao autenticacao;    // Gestor de autenticação
    private CacheDados cache;                   // Cache local de dados
    private Thread thread;                      // Thread de execução (UDP)
    private Thread threadTCP;                   // Thread de envio TCP
    private long totalMensagensRecebidas;       // Contador de mensagens
    private long totalMensagensValidas;         // Contador de mensagens válidas
    private long totalMensagensInvalidas;       // Contador de mensagens inválidas
    
    // ======= TCP / Datacenter Integration =======
    private ClienteTCP clienteTCP;              // Cliente TCP para Datacenter
    private String datacenterHost;              // Host do Datacenter
    private int datacenterPorta;                // Porta TCP do Datacenter
    private String edgeId;                      // ID único deste Edge Server
    private int intervaloEnvioBatch;            // Intervalo em ms para enviar batches
    private int tamanhoBatch;                   // Número de leituras por batch
    private long totalBatchesEnviados;          // Contador de batches enviados

    public EdgeServer(int porta) {
        this(porta, null, 0, "EDGE_" + System.currentTimeMillis(), 30000, 50);
    }

    public EdgeServer(int porta, String datacenterHost, int datacenterPorta, 
                      String edgeId, int intervaloEnvioBatch, int tamanhoBatch) {
        this.porta = porta;
        this.executando = false;
        this.autenticacao = new GestorAutenticacao();
        this.cache = new CacheDados(1000);
        this.totalMensagensRecebidas = 0;
        this.totalMensagensValidas = 0;
        this.totalMensagensInvalidas = 0;
        
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

    private void processarMensagem(byte[] dadosCifrados, String origem) {
        try {
            // Obter chaves de sessão do KeyManager
            SessionKeys keys = new SessionKeys(
                "EDGE_SERVER",
                KeyManager.getAESKey(),
                KeyManager.getHMACKey(),
                System.currentTimeMillis(),
                System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 horas
            );
            
            // Decifrar mensagem (dadosCifrados já é byte[])
            UdpMessage mensagem = UdpMessage.decryptMessage(dadosCifrados, keys);

            if (mensagem == null) {
                totalMensagensInvalidas++;
                System.err.println("[EdgeServer] ❌ Mensagem inválida de " + origem + " (HMAC/Decriptação falhou)");
                return;
            }

            // Verificar se é mensagem de autenticação (requisição de JWT)
            if (mensagem.getType() == MessageType.SENSOR_AUTH_REQUEST) {
                processarAutenticacao(mensagem, origem, keys);
                return;
            }

            // Para mensagens de dados, validar JWT
            boolean autenticado = autenticacao.autenticarComJWT(mensagem.getSensorId(), mensagem.getCredenciais());

            if (!autenticado) {
                totalMensagensInvalidas++;
                System.err.println("[EdgeServer] 🚫 Autenticação JWT falhou: " + mensagem.getSensorId() + " de " + origem);
                return;
            }

            totalMensagensValidas++;

            if (mensagem.getType() == MessageType.SENSOR_REGISTER) {
                System.out.println("[EdgeServer] ✅ REGISTER: " + mensagem.getSensorId() + " de " + origem);
            }

            cache.adicionarLeitura(mensagem.getSensorId(), mensagem.getDados());

            analisarDados(mensagem.getSensorId(), mensagem.getDados());

            if (DebugConfig.DEBUG_MODE) {
                System.out.printf("[EdgeServer] 📊 %s: Temp=%.1f°C, CO2=%.0f ppm, PM2.5=%.1f µg/m³%n",
                    mensagem.getSensorId(),
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

    private void processarAutenticacao(UdpMessage mensagem, String origem, SessionKeys keys) {
        String sensorId = mensagem.getSensorId();
        String senha = mensagem.getCredenciais();
        
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[EdgeServer] 🔐 Requisição de autenticação de: " + sensorId + " de " + origem);
        }
        
        // Gerar JWT usando GestorAutenticacao
        String jwt = autenticacao.registrarESensorEObterJWT(sensorId, senha);
        
        if (jwt == null) {
            totalMensagensInvalidas++;
            System.err.println("[EdgeServer] ❌ Autenticação falhou: " + sensorId + " de " + origem);
            enviarRespostaAuth(origem, MessageType.SENSOR_AUTH_FAILED, null, keys);
            return;
        }
        
        totalMensagensValidas++;
        System.out.println("[EdgeServer] ✅ JWT gerado para sensor: " + sensorId + " de " + origem);
        enviarRespostaAuth(origem, MessageType.SENSOR_AUTH_SUCCESS, jwt, keys);
    }

    private void enviarRespostaAuth(String destino, MessageType tipoResposta, String jwt, SessionKeys keys) {
        try {
            // Parsear endereço (formato: "IP:porta")
            String[] parts = destino.split(":");
            if (parts.length != 2) {
                System.err.println("[EdgeServer] ❌ Formato de destino inválido: " + destino);
                return;
            }
            
            String ip = parts[0];
            int porta = Integer.parseInt(parts[1]);
            
            // Criar mensagem de resposta
            UdpMessage resposta = new UdpMessage(
                tipoResposta,
                "EDGE_SERVER",
                jwt != null ? jwt : "AUTH_FAILED",
                null  // Sem dados ambientais
            );
            
            // Cifrar e enviar
            byte[] dadosCifrados = resposta.encrypt(keys);
            DatagramPacket pacote = new DatagramPacket(
                dadosCifrados, 
                dadosCifrados.length, 
                java.net.InetAddress.getByName(ip), 
                porta
            );
            
            socket.send(pacote);
            
            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[EdgeServer] 📤 Resposta de autenticação enviada para " + destino + 
                    " (tipo: " + tipoResposta + ")");
            }
            
        } catch (Exception e) {
            System.err.println("[EdgeServer] ❌ Erro ao enviar resposta de autenticação: " + e.getMessage());
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

    public boolean isExecutando() {
        return executando;
    }

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
