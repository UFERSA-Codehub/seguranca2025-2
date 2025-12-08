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
    private String authHost;
    private int authPort;

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
            logger.error("[Sensor {}] Resposta invalida do Discovery", sensorId);
            return false;
        }

        if (!channel.handleChallenge(challenge)) {
            logger.error("[Sensor {}] Falha ao processar CHALLENGE do Discovery", sensorId);
            return false;
        }
        logger.info("[Sensor {}] Handshake com Discovery concluido", sensorId);
        return true;
    }

    public boolean discoverServices() {
        logger.info("[Sensor {}] Buscando servicos (Edge + AuthServer)...", sensorId);

        MessageUDP lookEdge = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.LOOK_EDGE, "");
        if (lookEdge == null) {
            logger.error("[Sensor {}] Falha ao construir mensagem LOOK_EDGE", sensorId);
            return false;
        }
        channel.send(lookEdge, discoveryHost, discoveryPort);

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
            logger.error("[Sensor {}] Nenhum servico disponivel", sensorId);
            return false;
        }

        JsonObject payload = gson.fromJson(envelope.getPayload(), JsonObject.class);
        
        if (payload.has("edge")) {
            String[] edgeParts = payload.get("edge").getAsString().split(":");
            this.edgeHost = edgeParts[0];
            this.edgePort = Integer.parseInt(edgeParts[1]);
            logger.info("[Sensor {}] Edge encontrado: {}:{}", sensorId, edgeHost, edgePort);
        } else {
            logger.error("[Sensor {}] Edge nao encontrado na resposta", sensorId);
            return false;
        }
        
        if (payload.has("auth")) {
            String[] authParts = payload.get("auth").getAsString().split(":");
            this.authHost = authParts[0];
            this.authPort = Integer.parseInt(authParts[1]);
            logger.info("[Sensor {}] AuthServer encontrado: {}:{}", sensorId, authHost, authPort);
        } else {
            logger.warn("[Sensor {}] AuthServer nao encontrado na resposta", sensorId);
        }
        
        return true;
    }

    public String getEdgeHost() {
        return edgeHost;
    }

    public int getEdgePort() {
        return edgePort;
    }

    public String getAuthHost() {
        return authHost;
    }

    public int getAuthPort() {
        return authPort;
    }
}
