package com.project.server.datacenter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import com.project.message.udp.EnvelopeUDP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;

public class UdpClient {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter.UdpClient");
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;

    private final String name;
    private final SecureUDPChannel channel;
    private final String discoveryHost;
    private final int discoveryPort;

    private ScheduledExecutorService heartbeatScheduler;

    public UdpClient(String name, SecureUDPChannel channel, String discoveryHost, int discoveryPort) {
        this.name = name;
        this.channel = channel;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
    }

    public boolean handshake() {
        String peerInfo = discoveryHost + ":" + discoveryPort;

        channel.setTracePeerId("DISCOVERY");
        channel.send(channel.buildHello(), discoveryHost, discoveryPort);
        logger.debug("HELLO enviado para DISCOVERY ({})", peerInfo);

        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("Timeout aguardando CHALLENGE de DISCOVERY ({})", peerInfo);
            return false;
        }
        MessageUDP challenge = packet.message();
        if (challenge == null || challenge.getType() != MessageTypeUDP.CHALLENGE) {
            logger.error("Resposta inesperada de DISCOVERY ({}): {}", peerInfo, challenge != null ? challenge.getType() : "null");
            return false;
        }
        logger.debug("CHALLENGE recebido de DISCOVERY ({})", peerInfo);

        if (!channel.handleChallenge(challenge)) {
            logger.error("Falha ao processar CHALLENGE de DISCOVERY ({})", peerInfo);
            return false;
        }
        return true;
    }

    public boolean register(int tcpPort, int httpPort) {
        String peerInfo = discoveryHost + ":" + discoveryPort;

        // Construir payload
        JsonObject payload = new JsonObject();
        payload.addProperty("dataCenterId", name);
        payload.addProperty("tcpPort", tcpPort);
        payload.addProperty("httpPort", httpPort);

        // Enviar REGISTER_DATACENTER com envelope cifrado
        MessageUDP registerMsg = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.REGISTER_DATACENTER, payload.toString());
        if (registerMsg == null) {
            logger.error("Falha ao construir mensagem REGISTER_DATACENTER");
            return false;
        }
        channel.send(registerMsg, discoveryHost, discoveryPort);
        logger.info("REGISTER_DATACENTER enviado para DISCOVERY ({})", peerInfo);

        // Receber resposta (envelope cifrado)
        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("Timeout aguardando REGISTER_OK de DISCOVERY ({})", peerInfo);
            return false;
        }

        MessageUDP response = packet.message();
        if (response == null) {
            logger.error("Resposta nula de DISCOVERY ({})", peerInfo);
            return false;
        }

        // Verificar e decifrar envelope
        if (!channel.verify(response)) {
            logger.error("Falha ao verificar resposta de DISCOVERY ({})", peerInfo);
            return false;
        }

        EnvelopeUDP envelope = channel.decryptEnvelope("DISCOVERY", response);
        if (envelope == null) {
            logger.error("Falha ao decifrar envelope de DISCOVERY ({})", peerInfo);
            return false;
        }

        if (envelope.getType() != MessageTypeUDP.REGISTER_OK) {
            logger.error("Resposta inesperada de DISCOVERY ({}): {}", peerInfo, envelope.getType());
            return false;
        }

        logger.info("REGISTER_OK recebido de DISCOVERY ({})", peerInfo);
        return true;
    }

    public void startHeartbeatScheduler() {
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        logger.info("Heartbeat scheduler iniciado - intervalo: {}s", HEARTBEAT_INTERVAL_SECONDS);
    }

    private void sendHeartbeat() {
        MessageUDP heartbeat = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.HEARTBEAT, "");
        if (heartbeat != null) {
            channel.send(heartbeat, discoveryHost, discoveryPort);
            logger.debug("HEARTBEAT enviado para DISCOVERY");
        }
    }

    public void stop() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
