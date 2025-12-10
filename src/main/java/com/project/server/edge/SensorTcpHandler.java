package com.project.server.edge;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.project.auth.JWT;
import com.project.crypto.KeyManager;
import com.project.message.tcp.EnvelopeTCP;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;
import com.project.server.edge.data.AlertDetector;
import com.project.server.edge.data.Cache;

public class SensorTcpHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("Edge.SensorTcpHandler");
    private static final Gson gson = new Gson();
    private static final long REPLAY_WINDOW_MS = 30_000;

    private final Socket clientSocket;
    private final KeyManager serverKeyManager;
    private final JWT jwt;
    private final Cache cache;
    private final Set<String> usedNonces;
    private final ServerEdge serverEdge;
    private final String clientIp;
    private volatile boolean forceTerminated = false;

    private SecureTCPChannel channel;
    private String peerId;

    public SensorTcpHandler(Socket clientSocket, KeyManager serverKeyManager, JWT jwt, Cache cache, ServerEdge serverEdge) {
        this.clientSocket = clientSocket;
        this.serverKeyManager = serverKeyManager;
        this.jwt = jwt;
        this.cache = cache;
        this.usedNonces = new HashSet<>();
        this.serverEdge = serverEdge;
        this.clientIp = extractIp(clientSocket);
    }

    private String extractIp(Socket socket) {
        String address = socket.getRemoteSocketAddress().toString();
        if (address.startsWith("/")) {
            address = address.substring(1);
        }
        int colonIndex = address.lastIndexOf(':');
        if (colonIndex > 0) {
            return address.substring(0, colonIndex);
        }
        return address;
    }

    public void forceClose() {
        this.forceTerminated = true;
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.debug("Erro ao fechar socket forcadamente: {}", e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            clientSocket.setSoTimeout(60_000);
            this.channel = new SecureTCPChannel("EDGE", serverKeyManager, clientSocket);

            if (!performHandshake()) {
                logger.warn("Falha no handshake com {}", clientSocket.getRemoteSocketAddress());
                return;
            }

            handleMessages();

        } catch (IOException e) {
            logger.error("Erro de I/O: {}", e.getMessage());
        } finally {
            cleanup();
        }
    }

    private boolean performHandshake() {
        MessageTCP hello = channel.receive();
        if (hello == null || hello.getType() != MessageTypeTCP.HELLO) {
            logger.warn("Esperava HELLO, recebeu: {}", hello != null ? hello.getType() : "null");
            return false;
        }

        this.peerId = hello.getSenderId();
        logger.info("HELLO recebido de '{}'", peerId);

        MessageTCP challenge = channel.handleHello(hello);
        if (challenge == null) {
            logger.error("Falha ao processar HELLO de '{}'", peerId);
            return false;
        }

        channel.send(challenge);
        logger.info("CHALLENGE enviado para '{}'", peerId);

        return true;
    }

    private void handleMessages() {
        while (!clientSocket.isClosed()) {
            MessageTCP message = channel.receive();
            if (message == null) {
                logger.debug("Conexao fechada por '{}'", peerId);
                break;
            }

            if (!channel.isEncryptedEnvelope(message)) {
                logger.warn("Mensagem nao cifrada de '{}' - ignorando", peerId);
                continue;
            }

            if (!channel.verify(message)) {
                logger.warn("Falha na verificacao de mensagem de '{}'", peerId);
                continue;
            }

            EnvelopeTCP envelope = channel.decryptEnvelope(peerId, message);
            if (envelope == null) {
                logger.warn("Falha ao decifrar envelope de '{}'", peerId);
                continue;
            }

            if (!verifyReplayProtection(envelope)) {
                logger.warn("Replay detectado de '{}'", peerId);
                continue;
            }

            switch (envelope.getType()) {
                case DATA -> handleData(envelope);
                default -> logger.warn("Tipo de mensagem nao suportado: {}", envelope.getType());
            }
        }
    }

    private void handleData(EnvelopeTCP envelope) {
        String token = envelope.getJwtToken();
        if (token == null || !jwt.isValid(token)) {
            logger.warn("Token JWT invalido ou ausente de '{}'", peerId);
            sendError("Token JWT invalido");
            return;
        }

        String sensorId = jwt.getSensorId(token);
        String payload = envelope.getPayload();
        if (payload == null) {
            logger.error("Payload vazio em DATA de '{}'", peerId);
            sendError("Payload vazio");
            return;
        }

        JsonObject sensorData = gson.fromJson(payload, JsonObject.class);

        String alertType = AlertDetector.detectAlert(sensorData);
        boolean isAlert = alertType != null;

        cache.store(sensorId, sensorData, isAlert, alertType);

        if (isAlert) {
            logger.warn("ALERTA [{}] detectado nos dados de '{}'", alertType.toUpperCase(), sensorId);
        }

        if (AlertDetector.detectAnomaly(sensorData)) {
            logger.warn("ANOMALIA detectada nos dados de '{}': {}", sensorId, payload);
        }

        MessageTCP response = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.DATA_OK, "OK");
        if (response != null) {
            channel.send(response);
        }

        logger.debug("DATA processado de '{}': {} registros no cache", sensorId, cache.getCount());
    }

    private void sendError(String reason) {
        JsonObject errorPayload = new JsonObject();
        errorPayload.addProperty("reason", reason);

        MessageTCP response = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.ERROR, errorPayload.toString());
        if (response != null) {
            channel.send(response);
        }
    }

    private boolean verifyReplayProtection(EnvelopeTCP envelope) {
        long timestamp = envelope.getTimestamp();
        String nonce = envelope.getNonce();

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > REPLAY_WINDOW_MS) {
            logger.warn("Timestamp fora da janela de replay");
            return false;
        }

        if (usedNonces.contains(nonce)) {
            logger.warn("Nonce ja utilizado: {}", nonce);
            return false;
        }

        usedNonces.add(nonce);
        return true;
    }

    private void cleanup() {
        if (serverEdge != null) {
            serverEdge.unregisterConnection(clientIp);
        }
        if (forceTerminated) {
            logger.warn("Conexao de '{}' (IP: {}) terminada pelo IDS", peerId, clientIp);
        }
        if (channel != null) {
            if (peerId != null) {
                channel.clearPeerSession(peerId);
            }
            channel.close();
        }
    }
}
