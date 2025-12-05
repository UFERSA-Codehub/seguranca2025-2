package com.project.client.sensor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.project.message.udp.EnvelopeUDP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;

public class UdpClient {
    private static final Logger logger = LoggerFactory.getLogger("Sensor.UdpClient");
    private static final Gson gson = new Gson();

    private final String sensorId;
    private final SecureUDPChannel channel;
    private final String discoveryHost;
    private final int discoveryPort;

    private String edgeHost;
    private int edgePort;

    public UdpClient(String sensorId, SecureUDPChannel channel, String discoveryHost, int discoveryPort) {
        this.sensorId = sensorId;
        this.channel = channel;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
    }

    public boolean handshakeWithDiscovery() {
        logger.info("[Sensor {}] Handshake com Discovery ({}:{})...", sensorId, discoveryHost, discoveryPort);

        channel.send(channel.buildHello(), discoveryHost, discoveryPort);

        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("[Sensor {}] Timeout aguardando resposta do Discovery", sensorId);
            return false;
        }
        MessageUDP challenge = packet.message();
        if (challenge == null || challenge.getType() != MessageTypeUDP.CHALLENGE) {
            logger.error("[Sensor {}] Resposta inválida do Discovery", sensorId);
            return false;
        }

        if (!channel.handleChallenge(challenge)) {
            logger.error("[Sensor {}] Falha ao processar CHALLENGE do Discovery", sensorId);
            return false;
        }
        logger.info("[Sensor {}] Handshake com Discovery concluído", sensorId);
        return true;
    }

    public boolean discoverEdge() {
        logger.info("[Sensor {}] Buscando Edge...", sensorId);

        // Enviar LOOK_EDGE com envelope cifrado
        MessageUDP lookEdge = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.LOOK_EDGE, "");
        if (lookEdge == null) {
            logger.error("[Sensor {}] Falha ao construir mensagem LOOK_EDGE", sensorId);
            return false;
        }
        channel.send(lookEdge, discoveryHost, discoveryPort);

        // Receber resposta (envelope cifrado)
        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("[Sensor {}] Timeout aguardando resposta do Discovery", sensorId);
            return false;
        }
        MessageUDP response = packet.message();
        if (response == null) {
            logger.error("[Sensor {}] Resposta nula do Discovery", sensorId);
            return false;
        }

        // Verificar e decifrar envelope
        if (!channel.verify(response)) {
            logger.error("[Sensor {}] Falha ao verificar resposta do Discovery", sensorId);
            return false;
        }

        EnvelopeUDP envelope = channel.decryptEnvelope("DISCOVERY", response);
        if (envelope == null) {
            logger.error("[Sensor {}] Falha ao decifrar envelope do Discovery", sensorId);
            return false;
        }

        if (envelope.getType() == MessageTypeUDP.NOT_FOUND) {
            logger.error("[Sensor {}] Nenhum Edge disponível", sensorId);
            return false;
        }

        // Extrair host:port do payload
        String payload = envelope.getPayload();
        String[] parts = payload.split(":");
        this.edgeHost = parts[0];
        this.edgePort = Integer.parseInt(parts[1]);
        logger.info("[Sensor {}] Edge encontrado: {}:{}", sensorId, edgeHost, edgePort);
        return true;
    }

    public boolean handshakeWithEdge() {
        logger.info("[Sensor {}] Handshake com Edge ({}:{})...", sensorId, edgeHost, edgePort);

        channel.send(channel.buildHello(), edgeHost, edgePort);

        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("[Sensor {}] Timeout aguardando resposta do Edge", sensorId);
            return false;
        }
        MessageUDP challenge = packet.message();
        if (challenge == null || challenge.getType() != MessageTypeUDP.CHALLENGE) {
            logger.error("[Sensor {}] Resposta inválida do Edge", sensorId);
            return false;
        }

        if (!channel.handleChallenge(challenge)) {
            logger.error("[Sensor {}] Falha ao processar CHALLENGE do Edge", sensorId);
            return false;
        }
        logger.info("[Sensor {}] Handshake com Edge concluído", sensorId);
        return true;
    }

    public String authenticate(String password) {
        logger.info("[Sensor {}] Autenticando com Edge...", sensorId);

        // Construir payload de autenticação
        JsonObject authPayload = new JsonObject();
        authPayload.addProperty("sensorId", sensorId);
        authPayload.addProperty("password", password);

        // Enviar AUTH com envelope cifrado
        MessageUDP authMsg = channel.buildEncryptedEnvelope("EDGE", MessageTypeUDP.AUTH, authPayload.toString());
        if (authMsg == null) {
            logger.error("[Sensor {}] Falha ao construir mensagem AUTH", sensorId);
            return null;
        }
        channel.send(authMsg, edgeHost, edgePort);

        // Receber resposta (envelope cifrado)
        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("[Sensor {}] Timeout aguardando resposta de autenticação", sensorId);
            return null;
        }
        MessageUDP response = packet.message();
        if (response == null) {
            logger.error("[Sensor {}] Resposta nula do Edge", sensorId);
            return null;
        }

        // Verificar e decifrar envelope
        if (!channel.verify(response)) {
            logger.error("[Sensor {}] Falha ao verificar resposta de autenticação", sensorId);
            return null;
        }

        EnvelopeUDP envelope = channel.decryptEnvelope("EDGE", response);
        if (envelope == null) {
            logger.error("[Sensor {}] Falha ao decifrar envelope de autenticação", sensorId);
            return null;
        }

        if (envelope.getType() == MessageTypeUDP.AUTH_FAIL) {
            logger.error("[Sensor {}] Autenticação falhou: {}", sensorId, envelope.getPayload());
            return null;
        }

        // Extrair JWT do payload
        String token = gson.fromJson(envelope.getPayload(), JsonObject.class).get("token").getAsString();
        logger.info("[Sensor {}] Autenticado com sucesso", sensorId);
        return token;
    }

    public void sendData(String dataJson, String jwtToken) {
        // Usar buildEncryptedEnvelope com JWT token
        MessageUDP dataMsg = channel.buildEncryptedEnvelope("EDGE", MessageTypeUDP.DATA, dataJson, jwtToken);
        if (dataMsg == null) {
            logger.error("[Sensor {}] Falha ao construir mensagem DATA", sensorId);
            return;
        }
        channel.send(dataMsg, edgeHost, edgePort);
    }

    public String getEdgeHost() {
        return edgeHost;
    }

    public int getEdgePort() {
        return edgePort;
    }
}
