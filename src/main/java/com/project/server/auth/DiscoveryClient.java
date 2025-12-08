package com.project.server.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import com.project.message.udp.EnvelopeUDP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;

public class DiscoveryClient {
    private static final Logger logger = LoggerFactory.getLogger("AuthServer.DiscoveryClient");

    private final String name;
    private final SecureUDPChannel channel;
    private final String discoveryHost;
    private final int discoveryPort;

    public DiscoveryClient(String name, SecureUDPChannel channel, String discoveryHost, int discoveryPort) {
        this.name = name;
        this.channel = channel;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
    }

    public boolean handshake() {
        String peerInfo = discoveryHost + ":" + discoveryPort;

        channel.send(channel.buildHello(), discoveryHost, discoveryPort);
        logger.info("HELLO enviado para DISCOVERY ({})", peerInfo);

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
        logger.info("CHALLENGE recebido de DISCOVERY ({})", peerInfo);

        if (!channel.handleChallenge(challenge)) {
            logger.error("Falha ao processar CHALLENGE de DISCOVERY ({})", peerInfo);
            return false;
        }

        return true;
    }

    public boolean register(int port) {
        String peerInfo = discoveryHost + ":" + discoveryPort;

        JsonObject payload = new JsonObject();
        payload.addProperty("authId", name);
        payload.addProperty("port", port);

        MessageUDP registerMsg = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.REGISTER_AUTH, payload.toString());
        if (registerMsg == null) {
            logger.error("Falha ao construir mensagem REGISTER_AUTH");
            return false;
        }
        channel.send(registerMsg, discoveryHost, discoveryPort);
        logger.info("REGISTER_AUTH enviado para DISCOVERY ({})", peerInfo);

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

    public void sendHeartbeat() {
        MessageUDP heartbeat = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.HEARTBEAT, "");
        if (heartbeat != null) {
            channel.send(heartbeat, discoveryHost, discoveryPort);
            logger.debug("HEARTBEAT enviado para DISCOVERY");
        }
    }
}
