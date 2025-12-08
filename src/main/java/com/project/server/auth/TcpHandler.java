package com.project.server.auth;

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

public class TcpHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("AuthServer.TcpHandler");
    private static final Gson gson = new Gson();
    private static final long REPLAY_WINDOW_MS = 30_000;

    private final Socket clientSocket;
    private final KeyManager serverKeyManager;
    private final JWT jwt;
    private final CredentialStore credentials;
    private final Set<String> usedNonces;

    private SecureTCPChannel channel;
    private String peerId;

    public TcpHandler(Socket clientSocket, KeyManager serverKeyManager, JWT jwt, CredentialStore credentials) {
        this.clientSocket = clientSocket;
        this.serverKeyManager = serverKeyManager;
        this.jwt = jwt;
        this.credentials = credentials;
        this.usedNonces = new HashSet<>();
    }

    @Override
    public void run() {
        try {
            clientSocket.setSoTimeout(30_000);
            this.channel = new SecureTCPChannel("AUTH", serverKeyManager, clientSocket);

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
                case AUTH -> handleAuth(envelope);
                case VALIDATE -> handleValidate(envelope);
                default -> logger.warn("Tipo de mensagem nao suportado: {}", envelope.getType());
            }
        }
    }

    private void handleAuth(EnvelopeTCP envelope) {
        JsonObject payload = gson.fromJson(envelope.getPayload(), JsonObject.class);
        String sensorId = payload.get("sensorId").getAsString();
        String password = payload.get("password").getAsString();

        logger.info("AUTH request de sensor '{}'", sensorId);

        if (credentials.validateSensor(sensorId, password)) {
            String token = jwt.generateToken(sensorId);
            
            JsonObject response = new JsonObject();
            response.addProperty("token", token);
            response.addProperty("expiresIn", 1800);

            MessageTCP responseMsg = channel.buildEncryptedEnvelope(
                peerId, MessageTypeTCP.AUTH_OK, response.toString()
            );
            channel.send(responseMsg);
            logger.info("AUTH_OK enviado para sensor '{}'", sensorId);
        } else {
            MessageTCP responseMsg = channel.buildEncryptedEnvelope(
                peerId, MessageTypeTCP.AUTH_FAIL, "Credenciais invalidas"
            );
            channel.send(responseMsg);
            logger.warn("AUTH_FAIL enviado para sensor '{}'", sensorId);
        }
    }

    private void handleValidate(EnvelopeTCP envelope) {
        JsonObject payload = gson.fromJson(envelope.getPayload(), JsonObject.class);
        String username = payload.get("username").getAsString();
        String password = payload.get("password").getAsString();

        logger.info("VALIDATE request para usuario '{}'", username);

        if (credentials.validateUser(username, password)) {
            String token = jwt.generateClientToken(username);
            
            JsonObject response = new JsonObject();
            response.addProperty("token", token);
            response.addProperty("expiresIn", 1800);

            MessageTCP responseMsg = channel.buildEncryptedEnvelope(
                peerId, MessageTypeTCP.VALIDATE_OK, response.toString()
            );
            channel.send(responseMsg);
            logger.info("VALIDATE_OK enviado para '{}'", peerId);
        } else {
            MessageTCP responseMsg = channel.buildEncryptedEnvelope(
                peerId, MessageTypeTCP.VALIDATE_FAIL, "Credenciais invalidas"
            );
            channel.send(responseMsg);
            logger.warn("VALIDATE_FAIL enviado para '{}'", peerId);
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
        if (channel != null) {
            if (peerId != null) {
                channel.clearPeerSession(peerId);
            }
            channel.close();
        }
    }
}
