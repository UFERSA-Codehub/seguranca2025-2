package com.project.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.project.message.udp.EnvelopeUDP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;

/**
 * Cliente UDP para comunicação com o Discovery.
 * Descobre serviços AuthServer e Datacenter para o ClientApp.
 */
public class UdpClient {
    private static final Logger logger = LoggerFactory.getLogger("Client.UdpClient");
    private static final Gson gson = new Gson();

    private final String clientId;
    private final SecureUDPChannel channel;
    private final String discoveryHost;
    private final int discoveryPort;

    private String authHost;
    private int authPort;
    private String datacenterHost;
    private int datacenterPort;

    public UdpClient(String clientId, SecureUDPChannel channel, String discoveryHost, int discoveryPort) {
        this.clientId = clientId;
        this.channel = channel;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
    }

    /**
     * Realiza handshake com o Discovery (HELLO -> CHALLENGE).
     */
    public boolean handshakeWithDiscovery() {
        logger.debug("[Client {}] Handshake com Discovery ({}:{})...", clientId, discoveryHost, discoveryPort);

        channel.setTracePeerId("DISCOVERY");
        channel.send(channel.buildHello(), discoveryHost, discoveryPort);

        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("[Client {}] Timeout aguardando resposta do Discovery", clientId);
            return false;
        }

        MessageUDP challenge = packet.message();
        if (challenge == null || challenge.getType() != MessageTypeUDP.CHALLENGE) {
            logger.error("[Client {}] Resposta inválida do Discovery", clientId);
            return false;
        }

        if (!channel.handleChallenge(challenge)) {
            logger.error("[Client {}] Falha ao processar CHALLENGE do Discovery", clientId);
            return false;
        }

        logger.debug("[Client {}] Handshake com Discovery concluído", clientId);
        return true;
    }

    /**
     * Descobre serviços necessários: AuthServer e Datacenter.
     * Faz duas requisições: LOOK_EDGE (para auth) e LOOK_DATACENTER (para datacenter).
     */
    public boolean discoverServices() {
        logger.info("[Client {}] Buscando serviços (AuthServer + Datacenter)...", clientId);

        // Passo 1 - Buscar AuthServer via LOOK_EDGE (retorna auth junto com edge)
        if (!discoverAuthServer()) {
            return false;
        }

        // Passo 2 - Buscar Datacenter via LOOK_DATACENTER
        if (!discoverDatacenter()) {
            return false;
        }

        return true;
    }

    private boolean discoverAuthServer() {
        channel.setTracePeerId("DISCOVERY");
        MessageUDP lookEdge = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.LOOK_EDGE, "");
        if (lookEdge == null) {
            logger.error("[Client {}] Falha ao construir mensagem LOOK_EDGE", clientId);
            return false;
        }
        channel.send(lookEdge, discoveryHost, discoveryPort);

        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("[Client {}] Timeout aguardando resposta do Discovery", clientId);
            return false;
        }

        MessageUDP response = packet.message();
        if (response == null) {
            logger.error("[Client {}] Resposta nula do Discovery", clientId);
            return false;
        }

        if (!channel.verify(response)) {
            logger.error("[Client {}] Falha ao verificar resposta do Discovery", clientId);
            return false;
        }

        EnvelopeUDP envelope = channel.decryptEnvelope("DISCOVERY", response);
        if (envelope == null) {
            logger.error("[Client {}] Falha ao decifrar envelope do Discovery", clientId);
            return false;
        }

        if (envelope.getType() == MessageTypeUDP.NOT_FOUND) {
            logger.error("[Client {}] Nenhum serviço disponível", clientId);
            return false;
        }

        JsonObject payload = gson.fromJson(envelope.getPayload(), JsonObject.class);

        if (payload.has("auth")) {
            String[] authParts = payload.get("auth").getAsString().split(":");
            this.authHost = authParts[0];
            this.authPort = Integer.parseInt(authParts[1]);
            logger.info("[Client {}] AuthServer encontrado: {}:{}", clientId, authHost, authPort);
        } else {
            logger.error("[Client {}] AuthServer não encontrado na resposta", clientId);
            return false;
        }

        return true;
    }

    private boolean discoverDatacenter() {
        channel.setTracePeerId("DISCOVERY");
        
        // Solicitar porta TCP do Datacenter para clientes CLI
        // Usa "http" pois é o campo httpPort/clientTcpPort no registro do Datacenter
        JsonObject lookPayload = new JsonObject();
        lookPayload.addProperty("protocol", "http");
        
        MessageUDP lookDc = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.LOOK_DATACENTER, lookPayload.toString());
        if (lookDc == null) {
            logger.error("[Client {}] Falha ao construir mensagem LOOK_DATACENTER", clientId);
            return false;
        }
        channel.send(lookDc, discoveryHost, discoveryPort);

        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("[Client {}] Timeout aguardando resposta do Discovery", clientId);
            return false;
        }

        MessageUDP response = packet.message();
        if (response == null) {
            logger.error("[Client {}] Resposta nula do Discovery", clientId);
            return false;
        }

        if (!channel.verify(response)) {
            logger.error("[Client {}] Falha ao verificar resposta do Discovery", clientId);
            return false;
        }

        EnvelopeUDP envelope = channel.decryptEnvelope("DISCOVERY", response);
        if (envelope == null) {
            logger.error("[Client {}] Falha ao decifrar envelope do Discovery", clientId);
            return false;
        }

        if (envelope.getType() == MessageTypeUDP.NOT_FOUND) {
            logger.error("[Client {}] Nenhum Datacenter disponível", clientId);
            return false;
        }

        // Payload é "host:port"
        String[] parts = envelope.getPayload().split(":");
        this.datacenterHost = parts[0];
        this.datacenterPort = Integer.parseInt(parts[1]);
        logger.info("[Client {}] Datacenter encontrado: {}:{}", clientId, datacenterHost, datacenterPort);

        return true;
    }

    public String getAuthHost() {
        return authHost;
    }

    public int getAuthPort() {
        return authPort;
    }

    public String getDatacenterHost() {
        return datacenterHost;
    }

    public int getDatacenterPort() {
        return datacenterPort;
    }
}
