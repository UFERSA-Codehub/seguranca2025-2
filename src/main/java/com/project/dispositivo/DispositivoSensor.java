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

    public DispositivoSensor(Sensor metadados, String edgeServerHost, int edgeServerPort) {
        this(metadados, edgeServerHost, edgeServerPort, 2000, 3000);
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
        while (executando) {
            try {
                DadosAmbientais dados = DadosAmbientais.gerarAleatorio(metadados.getLocalizacao());
                
                metadados.atualizarLeitura(dados);

                MessageType tipo = registrado ? MessageType.SENSOR_UPDATE : MessageType.SENSOR_REGISTER;
                UdpMessage mensagem = new UdpMessage(
                    tipo, 
                    metadados.getId(), 
                    metadados.getCredenciais(), 
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
