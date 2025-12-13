package com.project.server.edge;

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
    private static final Logger logger = LoggerFactory.getLogger("Edge.UdpClient");
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;

    private final String name;
    private final SecureUDPChannel channel;
    private final String discoveryHost;
    private final int discoveryPort;

    private ScheduledExecutorService scheduler;

    public UdpClient(String name, SecureUDPChannel channel, String discoveryHost, int discoveryPort) {
        this.name = name;
        this.channel = channel;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
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

    public boolean register(int port) {
        String peerInfo = discoveryHost + ":" + discoveryPort;

        // Construir payload
        JsonObject payload = new JsonObject();
        payload.addProperty("edgeId", name);
        payload.addProperty("port", port);

        // Enviar REGISTER_EDGE com envelope cifrado
        MessageUDP registerMsg = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.REGISTER_EDGE, payload.toString());
        if (registerMsg == null) {
            logger.error("Falha ao construir mensagem REGISTER_EDGE");
            return false;
        }
        channel.send(registerMsg, discoveryHost, discoveryPort);
        logger.debug("REGISTER_EDGE enviado para DISCOVERY");

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

        logger.info("REGISTER_OK recebido de DISCOVERY");
        return true;
    }

    public String discoverDatacenter() {
        String peerInfo = discoveryHost + ":" + discoveryPort;

        // Construir payload com protocolo TCP
        JsonObject lookPayload = new JsonObject();
        lookPayload.addProperty("protocol", "tcp");

        // Enviar LOOK_DATACENTER com envelope cifrado
        MessageUDP lookMsg = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.LOOK_DATACENTER, lookPayload.toString());
        if (lookMsg == null) {
            logger.error("Falha ao construir mensagem LOOK_DATACENTER");
            return null;
        }
        channel.send(lookMsg, discoveryHost, discoveryPort);
        logger.debug("LOOK_DATACENTER enviado para DISCOVERY");

        // Receber resposta (envelope cifrado) - com retry para ignorar mensagens inesperadas
        // (ex: HEARTBEAT_OK de heartbeats anteriores)
        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++) {
            ReceivedPacket packet = channel.receive();
            if (packet == null) {
                logger.error("Timeout aguardando resposta de DISCOVERY ({})", peerInfo);
                return null;
            }

            MessageUDP response = packet.message();
            if (response == null) {
                logger.error("Resposta nula de DISCOVERY ({})", peerInfo);
                return null;
            }

            // Verificar e decifrar envelope
            if (!channel.verify(response)) {
                logger.error("Falha ao verificar resposta de DISCOVERY ({})", peerInfo);
                return null;
            }

            EnvelopeUDP envelope = channel.decryptEnvelope("DISCOVERY", response);
            if (envelope == null) {
                logger.error("Falha ao decifrar envelope de DISCOVERY ({})", peerInfo);
                return null;
            }

            // Ignorar HEARTBEAT_OK (pode chegar de heartbeats anteriores)
            if (envelope.getType() == MessageTypeUDP.HEARTBEAT_OK) {
                logger.debug("Ignorando HEARTBEAT_OK durante discoverDatacenter (tentativa {})", retry + 1);
                continue;
            }

            if (envelope.getType() == MessageTypeUDP.NOT_FOUND) {
                logger.warn("Nenhum Datacenter disponível no momento");
                return null;
            }

            if (envelope.getType() != MessageTypeUDP.FOUND_DATACENTER) {
                logger.error("Resposta inesperada de DISCOVERY ({}): {}", peerInfo, envelope.getType());
                return null;
            }

            String payload = envelope.getPayload();
            logger.info("Datacenter descoberto: {}", payload);
            return payload;
        }

        logger.error("Máximo de retries alcançado aguardando FOUND_DATACENTER de DISCOVERY ({})", peerInfo);
        return null;
    }

    public void startHeartbeatScheduler() {
        if (scheduler == null) {
            logger.error("Scheduler não configurado - use setScheduler() antes");
            return;
        }
        scheduler.scheduleAtFixedRate(
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
}
