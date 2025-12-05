package com.project.server.edge;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.project.auth.JWT;
import com.project.message.udp.EnvelopeUDP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.server.edge.data.AlertDetector;
import com.project.server.edge.data.Cache;

public class UdpHandler {
    private static final Logger logger = LoggerFactory.getLogger("Edge.UdpHandler");
    private static final Gson gson = new Gson();
    
    // Proteção contra replay attack
    private static final long MAX_MESSAGE_AGE_MS = 30_000; // 30 segundos
    private final Set<String> usedNonces = ConcurrentHashMap.newKeySet();

    private final SecureUDPChannel channel;
    private final JWT jwt;
    private final Cache cache;
    private final Map<String, String> credentialStore;
    private final Map<String, String> activeSessions;
    private final Runnable reRegisterCallback;

    public UdpHandler(
            SecureUDPChannel channel,
            JWT jwt,
            Cache cache,
            Map<String, String> credentialStore,
            Map<String, String> activeSessions,
            Runnable reRegisterCallback) {
        this.channel = channel;
        this.jwt = jwt;
        this.cache = cache;
        this.credentialStore = credentialStore;
        this.activeSessions = activeSessions;
        this.reRegisterCallback = reRegisterCallback;
    }

    public void handle(MessageUDP message, InetAddress clientAddress, int clientPort) {
        // Mensagens de handshake (sem criptografia)
        if (message.isHandshake()) {
            if (message.getType() == MessageTypeUDP.HELLO) {
                handleHello(message, clientAddress, clientPort);
            }
            return;
        }

        // Mensagem RE_REGISTER do Discovery (não cifrada)
        if (message.getType() == MessageTypeUDP.RE_REGISTER) {
            handleReRegister(message);
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
        logger.info("CHALLENGE enviado para {} ({})", senderId, peerInfo);
    }

    private void handleReRegister(MessageUDP message) {
        String senderId = message.getSenderId();
        logger.warn("RE_REGISTER recebido de {} - Discovery pode ter reiniciado", senderId);

        channel.clearPeerSession("DISCOVERY");

        if (reRegisterCallback != null) {
            reRegisterCallback.run();
        }
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

        // Passo 3 - Verificar proteção contra replay attack
        if (!verifyReplayProtection(envelope, senderId, peerInfo)) {
            return;
        }

        logger.debug("Envelope {} de {} ({})", envelope.getType(), senderId, peerInfo);

        // Passo 4 - Rotear baseado no type do envelope
        switch (envelope.getType()) {
            case AUTH -> handleAuth(senderId, envelope, clientAddress, clientPort);
            case DATA -> handleData(senderId, envelope, clientAddress, clientPort);
            default -> logger.warn("Tipo de envelope desconhecido: {} de {} ({})", envelope.getType(), senderId, peerInfo);
        }
    }

    // ==================== HANDLERS ====================

    private void handleAuth(String senderId, EnvelopeUDP envelope, InetAddress clientAddress, int clientPort) {
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.info("AUTH recebido de {} ({})", senderId, peerInfo);

        String payload = envelope.getPayload();
        if (payload == null) {
            logger.error("Payload vazio em AUTH de {} ({})", senderId, peerInfo);
            sendAuthFail(senderId, clientAddress, clientPort, "Payload vazio");
            return;
        }

        // Extrair credenciais
        JsonObject authData = gson.fromJson(payload, JsonObject.class);
        String sensorId = authData.get("sensorId").getAsString();
        String password = authData.get("password").getAsString();

        // Validar credenciais
        if (!validateCredentials(sensorId, password)) {
            logger.warn("Credenciais inválidas para sensor '{}' ({})", sensorId, peerInfo);
            sendAuthFail(senderId, clientAddress, clientPort, "Credenciais inválidas");
            return;
        }

        // Gerar token JWT
        String token = jwt.generateToken(sensorId);
        activeSessions.put(sensorId, token);

        // Enviar AUTH_OK com token
        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("token", token);
        responsePayload.addProperty("expiresIn", 1800);

        MessageUDP response = channel.buildEncryptedEnvelope(senderId, MessageTypeUDP.AUTH_OK, responsePayload.toString());
        if (response == null) {
            logger.error("Falha ao construir resposta AUTH_OK para {} ({})", senderId, peerInfo);
            return;
        }
        channel.send(response, clientAddress, clientPort);
        logger.info("AUTH_OK enviado para {} ({}) - token emitido", sensorId, peerInfo);
    }

    private void handleData(String senderId, EnvelopeUDP envelope, InetAddress clientAddress, int clientPort) {
        String peerInfo = clientAddress.getHostAddress() + ":" + clientPort;
        logger.debug("DATA recebido de {} ({})", senderId, peerInfo);

        // Passo 1 - Verificar token JWT do envelope
        String token = envelope.getJwtToken();
        if (token == null || !jwt.isValid(token)) {
            logger.warn("Token JWT inválido ou ausente de '{}' ({})", senderId, peerInfo);
            return;
        }

        // Passo 2 - Obter payload
        String payload = envelope.getPayload();
        if (payload == null) {
            logger.error("Payload vazio em DATA de {} ({})", senderId, peerInfo);
            return;
        }

        // Passo 3 - Parsear dados
        JsonObject sensorData = gson.fromJson(payload, JsonObject.class);

        // Passo 4 - Detectar alertas baseado em thresholds críticos
        String alertType = AlertDetector.detectAlert(sensorData);
        boolean isAlert = alertType != null;

        // Passo 5 - Armazenar no cache com informações de alerta
        cache.store(senderId, sensorData, isAlert, alertType);

        // Passo 6 - Log de alerta se detectado
        if (isAlert) {
            logger.warn("ALERTA [{}] detectado nos dados de {} ({})", alertType.toUpperCase(), senderId, peerInfo);
        }

        // Passo 7 - Detectar anomalias (valores fora da faixa)
        if (AlertDetector.detectAnomaly(sensorData)) {
            logger.warn("ANOMALIA detectada nos dados de {} ({}): {}", senderId, peerInfo, payload);
        }

        logger.info("Dados recebidos de {} ({}): {} registros no cache", senderId, peerInfo, cache.getCount());
    }

    // ==================== HELPERS ====================

    private void sendAuthFail(String peerId, InetAddress address, int port, String reason) {
        JsonObject failPayload = new JsonObject();
        failPayload.addProperty("reason", reason);

        MessageUDP response = channel.buildEncryptedEnvelope(peerId, MessageTypeUDP.AUTH_FAIL, failPayload.toString());
        if (response != null) {
            channel.send(response, address, port);
        }
    }

    private boolean validateCredentials(String sensorId, String password) {
        String storedPassword = credentialStore.get(sensorId);
        return storedPassword != null && storedPassword.equals(password);
    }

    private boolean verifyReplayProtection(EnvelopeUDP envelope, String senderId, String peerInfo) {
        // Verificar timestamp
        long now = System.currentTimeMillis();
        long messageAge = now - envelope.getTimestamp();
        
        if (messageAge > MAX_MESSAGE_AGE_MS || messageAge < -MAX_MESSAGE_AGE_MS) {
            logger.warn("Mensagem expirada de '{}' ({}) - idade: {}ms", senderId, peerInfo, messageAge);
            return false;
        }

        // Verificar nonce (proteção contra replay)
        String nonce = envelope.getNonce();
        if (nonce == null || !usedNonces.add(nonce)) {
            logger.warn("Replay attack detectado de '{}' ({}) - nonce duplicado", senderId, peerInfo);
            return false;
        }

        return true;
    }
}
