package com.project.dispositivo;

import com.project.model.DadosAmbientais;
import com.project.model.Sensor;
import com.project.security.DebugConfig;
import com.project.security.KeyManager;
import com.project.security.SessionKeys;
import com.project.messageBus.MessageType;
import com.project.messageBus.udp.UdpMessage;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

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

    public DispositivoSensor(Sensor metadados, String edgeServerHost, int edgeServerPort, String senha) {
        this(metadados, edgeServerHost, edgeServerPort, senha, 2000, 3000);
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
     * Autentica o sensor no EdgeServer e obtém JWT
     * @return true se autenticação foi bem-sucedida, false caso contrário
     */
    private boolean autenticarNoEdge() {
        try {
            System.out.println("[DispositivoSensor] Iniciando autenticação do sensor " + metadados.getId() + "...");

            // Criar mensagem de autenticação (sem dados ambientais)
            UdpMessage authRequest = new UdpMessage(
                MessageType.SENSOR_AUTH_REQUEST,
                metadados.getId(),
                senha,  // Senha em texto claro na mensagem
                null    // Sem dados ambientais
            );

            // Obter chaves de sessão para criptografia
            SessionKeys keys = new SessionKeys(
                metadados.getId(),
                KeyManager.getAESKey(),
                KeyManager.getHMACKey(),
                System.currentTimeMillis(),
                System.currentTimeMillis() + (24 * 60 * 60 * 1000)
            );

            // Cifrar mensagem
            byte[] dadosCifrados = authRequest.encrypt(keys);
            if (dadosCifrados == null) {
                System.err.println("[DispositivoSensor] Erro ao cifrar requisição de autenticação");
                return false;
            }

            // Enviar requisição
            InetAddress endereco = InetAddress.getByName(edgeServerHost);
            DatagramPacket pacoteEnvio = new DatagramPacket(dadosCifrados, dadosCifrados.length, endereco, edgeServerPort);
            socket.send(pacoteEnvio);

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[DispositivoSensor] Requisição de autenticação enviada. Aguardando resposta...");
            }

            // Configurar timeout para receber resposta (5 segundos)
            socket.setSoTimeout(5000);

            // Aguardar resposta
            byte[] bufferResposta = new byte[8192];
            DatagramPacket pacoteResposta = new DatagramPacket(bufferResposta, bufferResposta.length);
            socket.receive(pacoteResposta);

            // Descriptografar resposta
            byte[] dadosRecebidos = new byte[pacoteResposta.getLength()];
            System.arraycopy(pacoteResposta.getData(), 0, dadosRecebidos, 0, pacoteResposta.getLength());

            UdpMessage respostaAuth = UdpMessage.decryptMessage(dadosRecebidos, keys);
            if (respostaAuth == null) {
                System.err.println("[DispositivoSensor] Erro ao descriptografar resposta de autenticação");
                return false;
            }

            // Verificar tipo de resposta
            if (respostaAuth.getType() == MessageType.SENSOR_AUTH_SUCCESS) {
                // Extrair JWT do campo token
                jwtToken = respostaAuth.getToken();
                autenticado = true;

                if (DebugConfig.DEBUG_MODE) {
                    System.out.println("[DispositivoSensor] JWT recebido: " + jwtToken.substring(0, Math.min(50, jwtToken.length())) + "...");
                }

                return true;

            } else if (respostaAuth.getType() == MessageType.SENSOR_AUTH_FAILED) {
                System.err.println("[DispositivoSensor] Autenticação falhou: credenciais inválidas");
                return false;

            } else {
                System.err.println("[DispositivoSensor] Resposta inesperada do EdgeServer: " + respostaAuth.getType());
                return false;
            }

        } catch (java.net.SocketTimeoutException e) {
            System.err.println("[DispositivoSensor] Timeout ao aguardar resposta de autenticação do EdgeServer");
            return false;

        } catch (Exception e) {
            System.err.println("[DispositivoSensor] Erro durante autenticação: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
            return false;
        } finally {
            // Remover timeout para operações normais
            try {
                socket.setSoTimeout(0);
            } catch (Exception e) {
                // Ignorar erro ao resetar timeout
            }
        }
    }

    private boolean enviarMensagem(UdpMessage mensagem) {
        try {
            // Obter chaves de sessão do KeyManager
            SessionKeys keys = new SessionKeys(
                metadados.getId(),
                KeyManager.getAESKey(),
                KeyManager.getHMACKey(),
                System.currentTimeMillis(),
                System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 horas
            );
            
            // Cifrar mensagem
            byte[] dadosCifrados = mensagem.encrypt(keys);
            
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
