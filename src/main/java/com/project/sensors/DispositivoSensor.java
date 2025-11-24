package com.project.sensors;

import com.project.model.DadosAmbientais;
import com.project.model.Sensor;
import com.project.security.DebugConfig;
import com.project.security.KeyManager;
import com.project.security.SessionKeys;
import com.project.security.RSA;
import com.project.messageBus.MessageType;
import com.project.messageBus.udp.UdpMessage;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.Base64;
import javax.crypto.KeyGenerator;

public class DispositivoSensor implements Runnable {
    private Sensor metadados;               // Informações do sensor
    private String edgeServerHost;          // IP do servidor de borda
    private int edgeServerPort;             // Porta UDP do servidor
    private DatagramSocket socket;          // Socket UDP para comunicação
    private boolean executando;             // Flag de controle do loop
    private int intervaloMinMs;             // Intervalo mínimo entre envios (ms)
    private int intervaloMaxMs;             // Intervalo máximo entre envios (ms)
    private Random random;                  // Gerador de números aleatórios
    private boolean registrado;             // Indica se o sensor já foi registrado
    private Thread thread;                  // Thread de execução
    private String senha;                   // Senha para autenticação JWT
    private String jwtToken;                // Token JWT obtido do EdgeServer
    private boolean autenticado;            // Indica se o sensor foi autenticado com JWT
    private SessionKeys sessionKeys;        // Chaves de sessão únicas (geradas localmente)
    private RSA rsaEdge;                    // Chave pública RSA do EdgeServer

    public DispositivoSensor(Sensor metadados, String edgeServerHost, int edgeServerPort, String senha) {
        this(metadados, edgeServerHost, edgeServerPort, senha, 5000, 7000);
    }

    public DispositivoSensor(Sensor metadados, String edgeServerHost, int edgeServerPort, 
                            String senha, int intervaloMinMs, int intervaloMaxMs) {
        this.metadados = metadados;
        this.edgeServerHost = edgeServerHost;
        this.edgeServerPort = edgeServerPort;
        this.senha = senha;
        this.intervaloMinMs = intervaloMinMs;
        this.intervaloMaxMs = intervaloMaxMs;
        this.executando = false;
        this.registrado = false;
        this.autenticado = false;
        this.jwtToken = null;
        this.random = new Random();
    }

    public DispositivoSensor(Sensor metadados, String edgeServerHost, int edgeServerPort, 
                            int intervaloMinMs, int intervaloMaxMs) {
        this.metadados = metadados;
        this.edgeServerHost = edgeServerHost;
        this.edgeServerPort = edgeServerPort;
        this.intervaloMinMs = intervaloMinMs;
        this.intervaloMaxMs = intervaloMaxMs;
        this.executando = false;
        this.registrado = false;
        this.random = new Random();
    }

    public void iniciar() {
        if (executando) {
            System.err.println("[DispositivoSensor] Sensor " + metadados.getId() + " já está em execução!");
            return;
        }

        try {
            socket = new DatagramSocket();
            executando = true;
            thread = new Thread(this, "Sensor-" + metadados.getId());
            thread.start();

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[DispositivoSensor] Sensor " + metadados.getId() + 
                    " iniciado. Enviando para " + edgeServerHost + ":" + edgeServerPort);
            }

        } catch (Exception e) {
            System.err.println("[DispositivoSensor] Erro ao iniciar sensor " + metadados.getId() + ": " + e.getMessage());
            e.printStackTrace();
            executando = false;
        }
    }

    public void parar() {
        executando = false;
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (thread != null) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[DispositivoSensor] Sensor " + metadados.getId() + " parado.");
        }
    }

    @Override
    public void run() {
        // Autenticar antes de iniciar loop de dados
        if (!autenticarNoEdge()) {
            System.err.println("[DispositivoSensor] Falha na autenticação do sensor " + metadados.getId() + ". Encerrando...");
            executando = false;
            return;
        }

        System.out.println("[DispositivoSensor] Sensor " + metadados.getId() + " autenticado com sucesso!");

        while (executando) {
            try {
                DadosAmbientais dados = DadosAmbientais.gerarAleatorio(metadados.getLocalizacao());
                
                metadados.atualizarLeitura(dados);

                MessageType tipo = registrado ? MessageType.SENSOR_UPDATE : MessageType.SENSOR_REGISTER;
                UdpMessage mensagem = new UdpMessage(
                    tipo, 
                    metadados.getId(), 
                    jwtToken,  // Usar JWT ao invés de credenciais estáticas
                    dados
                );

                boolean enviado = enviarMensagem(mensagem);

                if (enviado && !registrado) {
                    registrado = true;
                    System.out.println("[DispositivoSensor] Sensor " + metadados.getId() + " registrado com sucesso.");
                }

                int intervalo = intervaloMinMs + random.nextInt(intervaloMaxMs - intervaloMinMs + 1);
                Thread.sleep(intervalo);

            } catch (InterruptedException e) {
                if (DebugConfig.DEBUG_MODE) {
                    System.out.println("[DispositivoSensor] Sensor " + metadados.getId() + " interrompido.");
                }
                break;
            } catch (Exception e) {
                System.err.println("[DispositivoSensor] Erro no loop do sensor " + metadados.getId() + ": " + e.getMessage());
                if (DebugConfig.DEBUG_MODE) {
                    e.printStackTrace();
                }
            }
        }

        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[DispositivoSensor] Thread do sensor " + metadados.getId() + " encerrada.");
        }
    }

    /**
     * Autentica o sensor no EdgeServer usando handshake RSA
     * Fluxo: HELLO → CHALLENGE → KEY_EXCHANGE → AUTH_SUCCESS
     * @return true se autenticação foi bem-sucedida, false caso contrário
     */
    private boolean autenticarNoEdge() {
        try {
            System.out.println("[DispositivoSensor] " + metadados.getId() + ": Iniciando handshake RSA...");

            // PASSO 1: Enviar SENSOR_HELLO (plaintext com sensorId + senha)
            UdpMessage hello = UdpMessage.createHello(metadados.getId(), senha);
            if (!enviarMensagemPlaintext(hello)) {
                System.err.println("[DispositivoSensor] Erro ao enviar HELLO");
                return false;
            }
            
            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[DispositivoSensor] " + metadados.getId() + ": HELLO enviado");
            }

            // PASSO 2: Receber SENSOR_CHALLENGE (plaintext com chave pública RSA)
            socket.setSoTimeout(5000); // 5 segundos timeout
            UdpMessage challenge = receberMensagemPlaintext();
            
            if (challenge == null || challenge.getType() != MessageType.SENSOR_CHALLENGE) {
                System.err.println("[DispositivoSensor] CHALLENGE não recebido ou inválido");
                return false;
            }
            
            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[DispositivoSensor] " + metadados.getId() + ": CHALLENGE recebido");
            }
            
            // Extrair chave pública RSA do EdgeServer
            String publicKeyBase64 = challenge.getPublicKeyBase64();
            rsaEdge = new RSA();
            java.security.PublicKey edgePublicKey = RSA.importarChavePublicaBase64(publicKeyBase64);
            
            if (edgePublicKey == null) {
                System.err.println("[DispositivoSensor] Erro ao importar chave pública do EdgeServer");
                return false;
            }

            // PASSO 3: Gerar SessionKeys aleatórias únicas
            javax.crypto.SecretKey aesKey = KeyGenerator.getInstance("AES").generateKey();
            javax.crypto.SecretKey hmacKey = KeyGenerator.getInstance("HmacSHA256").generateKey();
            
            sessionKeys = new SessionKeys(
                metadados.getId(),
                aesKey,
                hmacKey,
                System.currentTimeMillis(),
                System.currentTimeMillis() + (30 * 60 * 1000) // 30 min
            );
            
            // Serializar SessionKeys: "aesKeyBase64||hmacKeyBase64"
            String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKey.getEncoded());
            String hmacKeyBase64 = Base64.getEncoder().encodeToString(hmacKey.getEncoded());
            String keysJson = aesKeyBase64 + "||" + hmacKeyBase64;
            
            // Cifrar SessionKeys com chave pública RSA do EdgeServer
            byte[] keysBytes = keysJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] encryptedKeys = rsaEdge.cifrar(keysBytes, edgePublicKey);
            
            if (encryptedKeys == null) {
                System.err.println("[DispositivoSensor] Erro ao cifrar SessionKeys com RSA");
                return false;
            }
            
            // Enviar SENSOR_KEY_EXCHANGE (plaintext com SessionKeys criptografadas)
            UdpMessage keyExchange = UdpMessage.createKeyExchange(metadados.getId(), encryptedKeys);
            if (!enviarMensagemPlaintext(keyExchange)) {
                System.err.println("[DispositivoSensor] Erro ao enviar KEY_EXCHANGE");
                return false;
            }
            
            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[DispositivoSensor] " + metadados.getId() + ": KEY_EXCHANGE enviado");
            }

            // PASSO 4: Receber SENSOR_AUTH_SUCCESS (criptografado com SessionKeys)
            byte[] authResponseBytes = receberMensagemBytes();
            UdpMessage authSuccess = UdpMessage.decryptMessage(authResponseBytes, sessionKeys);
            
            if (authSuccess == null || authSuccess.getType() != MessageType.SENSOR_AUTH_SUCCESS) {
                System.err.println("[DispositivoSensor] AUTH_SUCCESS não recebido ou inválido");
                return false;
            }
            
            // Extrair JWT
            jwtToken = authSuccess.getCredenciais();
            autenticado = true;
            
            System.out.println("[DispositivoSensor] ✅ " + metadados.getId() + ": Handshake RSA completo!");
            
            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[DispositivoSensor] JWT: " + jwtToken.substring(0, Math.min(50, jwtToken.length())) + "...");
            }
            
            return true;

        } catch (java.net.SocketTimeoutException e) {
            System.err.println("[DispositivoSensor] Timeout durante handshake RSA");
            return false;

        } catch (Exception e) {
            System.err.println("[DispositivoSensor] Erro durante handshake RSA: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
            return false;
        } finally {
            // Remover timeout
            try {
                socket.setSoTimeout(0);
            } catch (Exception e) {
                // Ignorar
            }
        }
    }
    
    /**
     * Enviar mensagem plaintext (HELLO, KEY_EXCHANGE)
     */
    private boolean enviarMensagemPlaintext(UdpMessage mensagem) {
        try {
            String mensagemStr = mensagem.serializeToString();
            byte[] dados = mensagemStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            InetAddress endereco = InetAddress.getByName(edgeServerHost);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, edgeServerPort);
            socket.send(pacote);
            
            return true;
        } catch (Exception e) {
            System.err.println("[DispositivoSensor] Erro ao enviar mensagem plaintext: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Receber mensagem plaintext (CHALLENGE)
     */
    private UdpMessage receberMensagemPlaintext() {
        try {
            byte[] buffer = new byte[8192];
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
            socket.receive(pacote);
            
            byte[] dadosRecebidos = new byte[pacote.getLength()];
            System.arraycopy(pacote.getData(), 0, dadosRecebidos, 0, pacote.getLength());
            
            String mensagemStr = new String(dadosRecebidos, java.nio.charset.StandardCharsets.UTF_8);
            return UdpMessage.deserializeFromString(mensagemStr);
            
        } catch (Exception e) {
            if (DebugConfig.DEBUG_MODE) {
                System.err.println("[DispositivoSensor] Erro ao receber mensagem plaintext: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Receber mensagem em bytes (AUTH_SUCCESS criptografado)
     */
    private byte[] receberMensagemBytes() {
        try {
            byte[] buffer = new byte[8192];
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
            socket.receive(pacote);
            
            byte[] dadosRecebidos = new byte[pacote.getLength()];
            System.arraycopy(pacote.getData(), 0, dadosRecebidos, 0, pacote.getLength());
            
            return dadosRecebidos;
            
        } catch (Exception e) {
            if (DebugConfig.DEBUG_MODE) {
                System.err.println("[DispositivoSensor] Erro ao receber mensagem bytes: " + e.getMessage());
            }
            return null;
        }
    }

    private boolean enviarMensagem(UdpMessage mensagem) {
        try {
            // Usar SessionKeys do handshake (únicas para este sensor)
            if (sessionKeys == null) {
                System.err.println("[DispositivoSensor] SessionKeys não disponíveis (handshake não realizado)");
                return false;
            }
            
            // Cifrar mensagem com SessionKeys
            byte[] dadosCifrados = mensagem.encrypt(sessionKeys);
            
            if (dadosCifrados == null) {
                System.err.println("[DispositivoSensor] Erro ao cifrar mensagem do sensor " + metadados.getId());
                return false;
            }

            InetAddress endereco = InetAddress.getByName(edgeServerHost);
            DatagramPacket pacoteUDP = new DatagramPacket(dadosCifrados, dadosCifrados.length, endereco, edgeServerPort);

            socket.send(pacoteUDP);

            if (DebugConfig.DEBUG_MODE) {
                System.out.printf("[DispositivoSensor] %s enviou %s: Temp=%.1f°C, CO2=%.0f ppm, PM2.5=%.1f µg/m³%n",
                    metadados.getId(),
                    mensagem.getType(),
                    mensagem.getDados().getTemperatura(),
                    mensagem.getDados().getCo2(),
                    mensagem.getDados().getPm25()
                );
            }

            return true;

        } catch (Exception e) {
            System.err.println("[DispositivoSensor] Erro ao enviar mensagem do sensor " + metadados.getId() + ": " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
            return false;
        }
    }

    public boolean isExecutando() {
        return executando;
    }

    public boolean isRegistrado() {
        return registrado;
    }

    public Sensor getMetadados() {
        return metadados;
    }

    @Override
    public String toString() {
        return String.format("DispositivoSensor{id='%s', executando=%b, registrado=%b, destino=%s:%d}",
            metadados.getId(), executando, registrado, edgeServerHost, edgeServerPort);
    }
}
