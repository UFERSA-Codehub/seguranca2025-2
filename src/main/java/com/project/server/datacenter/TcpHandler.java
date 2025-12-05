package com.project.server.datacenter;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.message.tcp.EnvelopeTCP;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;
import com.project.server.datacenter.db.DataStore;

public class TcpHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("TcpHandler");
    private static final Gson gson = new Gson();

    // Chave secreta compartilhada entre Edge e Datacenter
    private static final String EDGE_SECRET = "EdgeDatacenterSharedSecret2025";

    private final int port;
    private final DataStore dataStore;
    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public TcpHandler(int port, DataStore dataStore, ExecutorService executor) {
        this.port = port;
        this.dataStore = dataStore;
        this.executor = executor;
        this.running = false;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("TcpHandler iniciado na porta {}", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("Conexão recebida de {}:{}", 
                            clientSocket.getInetAddress().getHostAddress(), 
                            clientSocket.getPort());

                    executor.submit(() -> handleConnection(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        logger.error("Erro ao aceitar conexão: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Erro ao iniciar TcpHandler na porta {}: {}", port, e.getMessage());
        }
    }

    private void handleConnection(Socket clientSocket) {
        String peerInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        SecureTCPChannel channel = null;

        try {
            // Passo 1 - Criar KeyManager e canal seguro
            KeyManager keyManager = new KeyManager();
            channel = new SecureTCPChannel("DATACENTER", keyManager, clientSocket);

            // Passo 2 - Receber HELLO
            MessageTCP hello = channel.receive();
            if (hello == null || !hello.isHandshake() || hello.getType() != MessageTypeTCP.HELLO) {
                logger.warn("Esperava HELLO de {}, recebeu: {}", peerInfo, hello != null ? hello.getType() : "null");
                return;
            }
            String edgeId = hello.getSenderId();
            logger.info("HELLO recebido de {} ({})", edgeId, peerInfo);

            // Passo 3 - Enviar CHALLENGE
            MessageTCP challenge = channel.handleHello(hello);
            if (challenge == null) {
                logger.error("Falha ao gerar CHALLENGE para {} ({})", edgeId, peerInfo);
                return;
            }
            channel.send(challenge);
            logger.info("CHALLENGE enviado para {} ({})", edgeId, peerInfo);

            // Passo 4 - Receber EDGE_AUTH (envelope cifrado) e validar secret
            MessageTCP authMsg = channel.receive();
            if (authMsg == null) {
                logger.warn("Não recebeu mensagem de autenticação de {} ({})", edgeId, peerInfo);
                return;
            }

            if (!handleEdgeAuth(channel, authMsg, edgeId, peerInfo)) {
                logger.warn("Autenticação falhou para {} ({})", edgeId, peerInfo);
                return;
            }
            logger.info("Edge {} ({}) autenticado com sucesso", edgeId, peerInfo);

            // Passo 5 - Loop de recebimento de dados
            while (running && !clientSocket.isClosed()) {
                MessageTCP message = channel.receive();
                if (message == null) {
                    logger.debug("Conexão fechada por {} ({})", edgeId, peerInfo);
                    break;
                }

                handleEnvelope(channel, message, edgeId, peerInfo);
            }
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao criar KeyManager para {}: {}", peerInfo, e.getMessage());
        } catch (IOException e) {
            logger.error("Erro de I/O com {}: {}", peerInfo, e.getMessage());
        } finally {
            if (channel != null) {
                channel.close();
            }
            logger.info("Conexão encerrada com {}", peerInfo);
        }
    }

    private boolean handleEdgeAuth(SecureTCPChannel channel, MessageTCP message, String edgeId, String peerInfo) {
        // Passo 1 - Verificar integridade da mensagem
        if (!channel.verify(message)) {
            logger.warn("EDGE_AUTH inválido de {} ({}) - verificação falhou", edgeId, peerInfo);
            sendAuthFail(channel, edgeId, "Verificação falhou");
            return false;
        }

        // Passo 2 - Decifrar envelope
        EnvelopeTCP envelope = channel.decryptEnvelope(edgeId, message);
        if (envelope == null) {
            logger.error("Falha ao decifrar EDGE_AUTH de {} ({})", edgeId, peerInfo);
            sendAuthFail(channel, edgeId, "Falha na decifração");
            return false;
        }

        // Passo 3 - Verificar tipo
        if (envelope.getType() != MessageTypeTCP.EDGE_AUTH) {
            logger.warn("Esperava EDGE_AUTH de {} ({}), recebeu: {}", edgeId, peerInfo, envelope.getType());
            sendAuthFail(channel, edgeId, "Tipo de mensagem inválido");
            return false;
        }

        // Passo 4 - Validar secret
        try {
            JsonObject authData = gson.fromJson(envelope.getPayload(), JsonObject.class);
            String receivedSecret = authData.get("secret").getAsString();

            if (!EDGE_SECRET.equals(receivedSecret)) {
                logger.warn("Secret inválido de {} ({})", edgeId, peerInfo);
                sendAuthFail(channel, edgeId, "Secret inválido");
                return false;
            }

            // Passo 5 - Enviar EDGE_AUTH_OK
            sendAuthOk(channel, edgeId);
            return true;

        } catch (Exception e) {
            logger.error("Erro ao processar EDGE_AUTH de {} ({}): {}", edgeId, peerInfo, e.getMessage());
            sendAuthFail(channel, edgeId, "Erro ao processar autenticação");
            return false;
        }
    }

    private void handleEnvelope(SecureTCPChannel channel, MessageTCP message, String edgeId, String peerInfo) {
        // Passo 1 - Verificar integridade e autenticidade
        if (!channel.verify(message)) {
            logger.warn("Envelope inválido de {} ({}) - verificação falhou", edgeId, peerInfo);
            sendError(channel, edgeId, "Verificação falhou");
            return;
        }

        // Passo 2 - Decifrar envelope
        EnvelopeTCP envelope = channel.decryptEnvelope(edgeId, message);
        if (envelope == null) {
            logger.error("Falha ao decifrar envelope de {} ({})", edgeId, peerInfo);
            sendError(channel, edgeId, "Falha na decifração");
            return;
        }

        logger.debug("Envelope {} de {} ({})", envelope.getType(), edgeId, peerInfo);

        // Passo 3 - Rotear baseado no type do envelope
        switch (envelope.getType()) {
            case DATA_BATCH -> handleDataBatch(channel, envelope, edgeId, peerInfo);
            default -> logger.warn("Tipo de envelope inesperado de {} ({}): {}", edgeId, peerInfo, envelope.getType());
        }
    }

    private void handleDataBatch(SecureTCPChannel channel, EnvelopeTCP envelope, String edgeId, String peerInfo) {
        String payload = envelope.getPayload();
        if (payload == null) {
            logger.error("Payload vazio em DATA_BATCH de {} ({})", edgeId, peerInfo);
            sendError(channel, edgeId, "Payload vazio");
            return;
        }

        try {
            JsonObject batch = gson.fromJson(payload, JsonObject.class);
            JsonArray readings = batch.getAsJsonArray("readings");

            int count = 0;
            for (JsonElement element : readings) {
                JsonObject reading = element.getAsJsonObject();

                String sensorId = reading.get("sensorId").getAsString();
                long timestamp = reading.get("timestamp").getAsLong();
                JsonObject data = reading.getAsJsonObject("data");
                boolean isAlert = reading.has("isAlert") && reading.get("isAlert").getAsBoolean();
                String alertType = reading.has("alertType") ? reading.get("alertType").getAsString() : null;

                dataStore.store(sensorId, timestamp, data, isAlert, alertType);
                count++;
            }

            logger.info("DATA_BATCH de {} ({}): {} leituras armazenadas", edgeId, peerInfo, count);
            sendDataAck(channel, edgeId, count);

        } catch (Exception e) {
            logger.error("Erro ao processar DATA_BATCH de {} ({}): {}", edgeId, peerInfo, e.getMessage());
            sendError(channel, edgeId, "Erro ao processar dados");
        }
    }

    // ==================== RESPOSTAS ====================

    private void sendAuthOk(SecureTCPChannel channel, String peerId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "ok");

        MessageTCP authOk = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.EDGE_AUTH_OK, payload.toString());
        if (authOk != null) {
            channel.send(authOk);
        }
    }

    private void sendAuthFail(SecureTCPChannel channel, String peerId, String reason) {
        JsonObject payload = new JsonObject();
        payload.addProperty("error", reason);

        MessageTCP authFail = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.EDGE_AUTH_FAIL, payload.toString());
        if (authFail != null) {
            channel.send(authFail);
        }
    }

    private void sendDataAck(SecureTCPChannel channel, String peerId, int count) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "ok");
        payload.addProperty("count", count);

        MessageTCP ack = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.DATA_ACK, payload.toString());
        if (ack != null) {
            channel.send(ack);
            logger.info("DATA_ACK enviado para {} ({} leituras)", peerId, count);
        }
    }

    private void sendError(SecureTCPChannel channel, String peerId, String reason) {
        JsonObject payload = new JsonObject();
        payload.addProperty("error", reason);

        MessageTCP error = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.ERROR, payload.toString());
        if (error != null) {
            channel.send(error);
        }
    }

    // ==================== CONTROLE ====================

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Erro ao fechar ServerSocket: {}", e.getMessage());
        }
        logger.info("TcpHandler parado");
    }

    public boolean isRunning() {
        return running;
    }
}
