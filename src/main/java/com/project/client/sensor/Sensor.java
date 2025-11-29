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
    private static final Logger logger = LoggerFactory.getLogger(Sensor.class);
    private static final int TIMEOUT_MS = 5000;

    private final String sensorId;
    private final String password;
    private final String discoveryHost;
    private final int discoveryPort;

    private SecureUDPChannel channel;
    private UdpClient udpClient;
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
            KeyManager keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            this.channel = new SecureUDPChannel(sensorId, keyManager, socket);
        } catch (NoSuchAlgorithmException e) {
            logger.error("[Sensor {}] Erro ao inicializar KeyManager: {}", sensorId, e.getMessage());
            return;
        } catch (SocketException e) {
            logger.error("[Sensor {}] Erro ao abrir socket: {}", sensorId, e.getMessage());
            return;
        }

        // Inicializar cliente UDP
        this.udpClient = new UdpClient(sensorId, channel, discoveryHost, discoveryPort);

        try {
            if (!udpClient.handshakeWithDiscovery()) { 
                logger.warn("[Sensor {}] Falha no handshake com Discovery", sensorId);
                return;
            }

            if (!udpClient.discoverEdge()) {
                logger.warn("[Sensor {}] Nenhum Edge disponível", sensorId);
                return;
            }

            if (!udpClient.handshakeWithEdge()) {
                logger.warn("[Sensor {}] Falha no handshake com Edge", sensorId);
                return;
            }

            this.jwtToken = udpClient.authenticate(password);
            if (jwtToken == null) {
                logger.warn("[Sensor {}] Falha na autenticação com Edge", sensorId);
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
                SensorData data = SensorData.generateRandom(sensorId);
                logger.info("[Sensor {}] {}", sensorId, data);
                udpClient.sendData(data.toJson(), jwtToken);
                Thread.sleep(2000 + (int)(Math.random() * 1000));
            } catch (InterruptedException e) {
                break;
            }
        }
        logger.info("[Sensor {}] Monitoramento encerrado", sensorId);
    }

    public void stop() {
        this.running = false;
        if (channel != null) {
            channel.getSocket().close();
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
