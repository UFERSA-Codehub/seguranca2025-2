package com.project.client.sensor;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.KeyManager;
import com.project.network.SecureUDPChannel;

public class Sensor {
    private static final Logger logger = LoggerFactory.getLogger("Sensor");
    private static final int TIMEOUT_MS = 5000;

    private final String sensorId;
    private final String password;
    private final String discoveryHost;
    private final int discoveryPort;

    private KeyManager keyManager;
    private SecureUDPChannel udpChannel;
    private UdpClient udpClient;
    private TcpClient tcpClient;
    private SensorDataGenerator dataGenerator;
    private String jwtToken;
    private volatile boolean running;

    public Sensor(String sensorId, String password, String discoveryHost, int discoveryPort) {
        this.sensorId = sensorId;
        this.password = password;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
    }

    public void start() {
        logger.info("[Sensor {}] Iniciando...", sensorId);
        try {
            this.keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            this.udpChannel = new SecureUDPChannel(sensorId, keyManager, socket);
        } catch (NoSuchAlgorithmException e) {
            logger.error("[Sensor {}] Erro ao inicializar KeyManager: {}", sensorId, e.getMessage());
            return;
        } catch (SocketException e) {
            logger.error("[Sensor {}] Erro ao abrir socket: {}", sensorId, e.getMessage());
            return;
        }

        this.udpClient = new UdpClient(sensorId, udpChannel, discoveryHost, discoveryPort);
        this.tcpClient = new TcpClient(sensorId, keyManager);
        this.dataGenerator = new SensorDataGenerator(sensorId);

        try {
            if (!udpClient.handshakeWithDiscovery()) { 
                logger.warn("[Sensor {}] Falha no handshake com Discovery", sensorId);
                return;
            }

            if (!udpClient.discoverServices()) {
                logger.warn("[Sensor {}] Nenhum servico disponivel", sensorId);
                return;
            }

            String authHost = udpClient.getAuthHost();
            int authPort = udpClient.getAuthPort();
            if (authHost == null) {
                logger.warn("[Sensor {}] AuthServer nao disponivel", sensorId);
                return;
            }

            this.jwtToken = tcpClient.authenticateWithAuthServer(authHost, authPort, password);
            if (jwtToken == null) {
                logger.warn("[Sensor {}] Falha na autenticacao com AuthServer", sensorId);
                return;
            }

            String edgeHost = udpClient.getEdgeHost();
            int edgePort = udpClient.getEdgePort();
            if (!tcpClient.connectToEdge(edgeHost, edgePort)) {
                logger.warn("[Sensor {}] Falha na conexao com Edge", sensorId);
                return;
            }

            this.running = true;
            startMonitoringLoop();
        } finally {
            stop();
        }
    }

    private void startMonitoringLoop() {
        logger.info("[Sensor {}] Iniciando monitoramento...", sensorId);
        long startTime = System.currentTimeMillis();
        long durationMs = 5 * 60 * 1000;
        while (running && (System.currentTimeMillis() - startTime) < durationMs) {
            try {
                SensorData data = dataGenerator.generate();
                logger.info("[Sensor {}] {}", sensorId, data);
                if (!tcpClient.sendData(data.toJson(), jwtToken)) {
                    logger.warn("[Sensor {}] Falha ao enviar dados - reconectando...", sensorId);
                    if (!reconnectToEdge()) {
                        logger.error("[Sensor {}] Falha ao reconectar - encerrando", sensorId);
                        break;
                    }
                }
                Thread.sleep(5000 + (int)(Math.random() * 3000));
            } catch (InterruptedException e) {
                break;
            }
        }
        logger.info("[Sensor {}] Monitoramento encerrado", sensorId);
    }

    private boolean reconnectToEdge() {
        tcpClient.closeEdgeChannel();
        return tcpClient.connectToEdge(udpClient.getEdgeHost(), udpClient.getEdgePort());
    }

    public void stop() {
        this.running = false;
        if (tcpClient != null) {
            tcpClient.closeEdgeChannel();
        }
        if (udpChannel != null) {
            udpChannel.getSocket().close();
        }
        logger.info("[Sensor {}] Parado", sensorId);
    }

    public static void main(String[] args) {
        String sensorId = args.length > 0 ? args[0] : "SENSOR_" + UUID.randomUUID().toString().substring(0, 8);
        String pass = args.length > 1 ? args[1] : "senha123";
        String host = args.length > 2 ? args[2] : "localhost";
        int port = args.length > 3 ? Integer.parseInt(args[3]) : 4000;

        Sensor sensor = new Sensor(sensorId, pass, host, port);
        Runtime.getRuntime().addShutdownHook(new Thread(sensor::stop));
        sensor.start();
    }
}
