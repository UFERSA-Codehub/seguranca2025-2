package com.project.server.discovery;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.project.message.udp.EnvelopeUDP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.server.ServiceInfo;

public class UdpHandler {
    private static final Logger logger = LoggerFactory.getLogger("Discovery.UdpHandler");
    private static final Gson gson = new Gson();

    private final String serverId;
    private final SecureUDPChannel channel;
    private final ServiceRegistry registry;

    public UdpHandler(String serverId, SecureUDPChannel channel, ServiceRegistry registry) {
        this.serverId = serverId;
        this.channel = channel;
        this.registry = registry;
    }

    public void handle(MessageUDP message, InetAddress clientAddress, int clientPort) {
        // Mensagens de handshake (sem criptografia)
        if (message.isHandshake()) {
            if (message.getType() == MessageTypeUDP.HELLO) {
                handleHello(message, clientAddress, clientPort);
            }
            return;
        }

        // Mensagens com envelope cifrado
        handleEnvelope(message, clientAddress, clientPort);
    }

    private void handleHello(MessageUDP message, InetAddress clientAddress, int clientPort) {
        String senderId = message.getSenderId();
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.info("HELLO recebido de {} ({})", senderId, peerInfo);
        
        MessageUDP challenge = channel.handleHello(message);
        if (challenge == null) {
            logger.error("Falha ao processar HELLO de {} ({})", senderId, peerInfo);
            return;
        }
        channel.send(challenge, clientAddress, clientPort);
        logger.debug("CHALLENGE enviado para {} ({})", senderId, peerInfo);
    }

    private void handleEnvelope(MessageUDP message, InetAddress clientAddress, int clientPort) {
        String senderId = message.getSenderId();
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;

        // Passo 1 - Verificar integridade e autenticidade
        if (!channel.verify(message)) {
            logger.warn("Envelope inválido de '{}' ({}) - verificação falhou", senderId, peerInfo);
            return;
        }

        // Passo 2 - Decifrar envelope
        EnvelopeUDP envelope = channel.decryptEnvelope(senderId, message);
        if (envelope == null) {
            logger.error("Falha ao decifrar envelope de '{}' ({})", senderId, peerInfo);
            return;
        }

        logger.debug("Envelope {} de {} ({})", envelope.getType(), senderId, peerInfo);

        // Passo 3 - Rotear baseado no type do envelope
        switch (envelope.getType()) {
            case LOOK_EDGE -> handleLookEdge(senderId, envelope, clientAddress, clientPort);
            case LOOK_DATACENTER -> handleLookDatacenter(senderId, envelope, clientAddress, clientPort);
            case REGISTER_EDGE -> handleRegisterEdge(senderId, envelope, clientAddress, clientPort);
            case REGISTER_DATACENTER -> handleRegisterDatacenter(senderId, envelope, clientAddress, clientPort);
            case REGISTER_AUTH -> handleRegisterAuth(senderId, envelope, clientAddress, clientPort);
            case HEARTBEAT -> handleHeartbeat(senderId, envelope, clientAddress, clientPort);
            default -> logger.warn("Tipo de envelope desconhecido: {} de {} ({})", envelope.getType(), senderId, peerInfo);
        }
    }

    // ==================== HANDLERS ====================

    private void handleLookEdge(String senderId, EnvelopeUDP envelope, InetAddress clientAddress, int clientPort) {
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.info("LOOK_EDGE de {}", senderId);

        MessageUDP response;
        if (!registry.hasEdges()) {
            logger.info("Nenhum EDGE disponível para {}", senderId);
            response = channel.buildEncryptedEnvelope(senderId, MessageTypeUDP.NOT_FOUND, "Nenhum EDGE disponível");
        } else {
            JsonObject responsePayload = new JsonObject();
            
            // Se firewall esta habilitado, retornar enderecos do PacketFilter
            // Senao, retornar enderecos reais dos servidores internos
            if (registry.isFirewallEnabled()) {
                responsePayload.addProperty("edge", registry.getExternalEdgeAddress());
                if (registry.hasAuthServers()) {
                    responsePayload.addProperty("auth", registry.getExternalAuthAddress());
                }
                logger.debug("Retornando enderecos do PacketFilter para {} ({})", senderId, peerInfo);
            } else {
                ServiceInfo edge = registry.getFirstEdge();
                responsePayload.addProperty("edge", edge.getHost() + ":" + edge.getPort());
                if (registry.hasAuthServers()) {
                    ServiceInfo auth = registry.getFirstAuthServer();
                    responsePayload.addProperty("auth", auth.getHost() + ":" + auth.getPort());
                }
            }
            
            response = channel.buildEncryptedEnvelope(senderId, MessageTypeUDP.FOUND_EDGE, responsePayload.toString());
        }

        if (response == null) {
            logger.error("Falha ao construir resposta para {} ({})", senderId, peerInfo);
            return;
        }
        channel.send(response, clientAddress, clientPort);
        logger.info("FOUND_EDGE enviado para {}", senderId);
    }

    private void handleLookDatacenter(String senderId, EnvelopeUDP envelope, InetAddress clientAddress, int clientPort) {
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.info("LOOK_DATACENTER de {}", senderId);

        // Extrair protocolo do payload do envelope
        String protocol = "tcp";
        String requestPayload = envelope.getPayload();
        if (requestPayload != null && !requestPayload.isEmpty()) {
            try {
                JsonObject request = gson.fromJson(requestPayload, JsonObject.class);
                if (request.has("protocol")) {
                    protocol = request.get("protocol").getAsString().toLowerCase();
                }
            } catch (Exception e) {
                logger.debug("Payload não é JSON válido, usando protocolo padrão (tcp)");
            }
        }

        MessageUDP response;
        if (!registry.hasDatacenters()) {
            logger.info("Nenhum DATACENTER disponível para {}", senderId);
            response = channel.buildEncryptedEnvelope(senderId, MessageTypeUDP.NOT_FOUND, "Nenhum DATACENTER disponível");
        } else {
            ServiceInfo dc = registry.getFirstDatacenter();
            int selectedPort = "http".equals(protocol) ? dc.getHttpPort() : dc.getPort();
            String responsePayload = dc.getHost() + ":" + selectedPort;
            response = channel.buildEncryptedEnvelope(senderId, MessageTypeUDP.FOUND_DATACENTER, responsePayload);
            logger.debug("DATACENTER {} selecionado para {} (protocolo: {})", dc.getServiceId(), senderId, protocol);
        }

        if (response == null) {
            logger.error("Falha ao construir resposta para {} ({})", senderId, peerInfo);
            return;
        }
        channel.send(response, clientAddress, clientPort);
        logger.info("FOUND_DATACENTER enviado para {} (protocolo: {})", senderId, protocol);
    }

    private void handleRegisterEdge(String senderId, EnvelopeUDP envelope, InetAddress clientAddress, int clientPort) {
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.info("REGISTER_EDGE de {} ({})", senderId, peerInfo);

        String payload = envelope.getPayload();
        if (payload == null) {
            logger.error("Payload vazio em REGISTER_EDGE de {} ({})", senderId, peerInfo);
            return;
        }

        JsonObject data = gson.fromJson(payload, JsonObject.class);
        String edgeId = data.get("edgeId").getAsString();
        int edgePort = data.get("port").getAsInt();
        String edgeHost = clientAddress.getHostAddress();

        registry.registerEdge(edgeId, edgeHost, edgePort);

        MessageUDP response = channel.buildEncryptedEnvelope(senderId, MessageTypeUDP.REGISTER_OK, "OK");
        if (response == null) {
            logger.error("Falha ao construir resposta REGISTER_OK para {} ({})", senderId, peerInfo);
            return;
        }
        channel.send(response, clientAddress, clientPort);
        logger.info("REGISTER_OK enviado para {} ({})", senderId, peerInfo);
    }

    private void handleRegisterDatacenter(String senderId, EnvelopeUDP envelope, InetAddress clientAddress, int clientPort) {
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.info("REGISTER_DATACENTER de {} ({})", senderId, peerInfo);

        String payload = envelope.getPayload();
        if (payload == null) {
            logger.error("Payload vazio em REGISTER_DATACENTER de {} ({})", senderId, peerInfo);
            return;
        }

        JsonObject data = gson.fromJson(payload, JsonObject.class);
        String dcId = data.get("dataCenterId").getAsString();
        int tcpPort = data.get("tcpPort").getAsInt();
        int httpPort = data.get("httpPort").getAsInt();
        String dcHost = clientAddress.getHostAddress();

        registry.registerDatacenter(dcId, dcHost, tcpPort, httpPort);

        MessageUDP response = channel.buildEncryptedEnvelope(senderId, MessageTypeUDP.REGISTER_OK, "OK");
        if (response == null) {
            logger.error("Falha ao construir resposta REGISTER_OK para {} ({})", senderId, peerInfo);
            return;
        }
        channel.send(response, clientAddress, clientPort);
        logger.info("REGISTER_OK enviado para {} ({})", senderId, peerInfo);
    }

    private void handleRegisterAuth(String senderId, EnvelopeUDP envelope, InetAddress clientAddress, int clientPort) {
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.info("REGISTER_AUTH de {} ({})", senderId, peerInfo);

        String payload = envelope.getPayload();
        if (payload == null) {
            logger.error("Payload vazio em REGISTER_AUTH de {} ({})", senderId, peerInfo);
            return;
        }

        JsonObject data = gson.fromJson(payload, JsonObject.class);
        String authId = data.get("authId").getAsString();
        int authPort = data.get("port").getAsInt();
        String authHost = clientAddress.getHostAddress();

        registry.registerAuthServer(authId, authHost, authPort);

        MessageUDP response = channel.buildEncryptedEnvelope(senderId, MessageTypeUDP.REGISTER_OK, "OK");
        if (response == null) {
            logger.error("Falha ao construir resposta REGISTER_OK para {} ({})", senderId, peerInfo);
            return;
        }
        channel.send(response, clientAddress, clientPort);
        logger.info("REGISTER_OK enviado para {} ({})", senderId, peerInfo);
    }

    private void handleHeartbeat(String senderId, EnvelopeUDP envelope, InetAddress clientAddress, int clientPort) {
        if (registry.updateEdgeLastSeen(senderId)) {
            logger.debug("HEARTBEAT de EDGE {}", senderId);
            return;
        }

        if (registry.updateDatacenterLastSeen(senderId)) {
            logger.debug("HEARTBEAT de DATACENTER {}", senderId);
            return;
        }

        if (registry.updateAuthServerLastSeen(senderId)) {
            logger.debug("HEARTBEAT de AUTH {}", senderId);
            return;
        }

        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.warn("HEARTBEAT de serviço não registrado: {} ({}) - solicitando RE_REGISTER", senderId, peerInfo);
        MessageUDP reRegister = new MessageUDP(MessageTypeUDP.RE_REGISTER, serverId);
        channel.send(reRegister, clientAddress, clientPort);
    }
}
