package com.project.server.datacenter;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

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
import com.project.server.datacenter.db.DataStore;
import com.project.server.datacenter.db.DataStore.SensorReading;
import com.project.server.datacenter.db.ReportService;

/**
 * Handler TCP para conexões de clientes CLI.
 * Escuta na porta 9090, realiza handshake, valida JWT e responde consultas de relatórios.
 * 
 * Fluxo:
 * 1. Cliente envia HELLO
 * 2. Servidor responde CHALLENGE
 * 3. Cliente envia QUERY_REPORT com JWT
 * 4. Servidor valida JWT, gera relatório, responde QUERY_RESPONSE
 */
public class ClientTcpHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter.ClientTcpHandler");
    private static final Gson gson = new Gson();
    private static final long REPLAY_WINDOW_MS = 30_000;

    private final int port;
    private final DataStore dataStore;
    private final ReportService reportService;
    private final ExecutorService executor;
    private final KeyManager keyManager;
    private final JWT jwt;
    
    private ServerSocket serverSocket;
    private volatile boolean running;

    public ClientTcpHandler(int port, DataStore dataStore, ReportService reportService, 
                            ExecutorService executor, KeyManager keyManager, JWT jwt) {
        this.port = port;
        this.dataStore = dataStore;
        this.reportService = reportService;
        this.executor = executor;
        this.keyManager = keyManager;
        this.jwt = jwt;
        this.running = false;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("[ClientTcpHandler] Iniciando na porta {}...", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("Conexão de cliente CLI recebida de {}:{}", 
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
            logger.error("Erro ao iniciar ClientTcpHandler na porta {}: {}", port, e.getMessage());
        }
    }

    private void handleConnection(Socket clientSocket) {
        String peerInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        SecureTCPChannel channel = null;
        String clientId = null;
        Set<String> usedNonces = new HashSet<>();

        try {
            clientSocket.setSoTimeout(30_000);
            channel = new SecureTCPChannel("DATACENTER", this.keyManager, clientSocket);

            // Passo 1 - Receber HELLO
            MessageTCP hello = channel.receive();
            if (hello == null || hello.getType() != MessageTypeTCP.HELLO) {
                logger.warn("Esperava HELLO de {}, recebeu: {}", peerInfo, hello != null ? hello.getType() : "null");
                return;
            }
            clientId = hello.getSenderId();
            // Trace para dashboard - mostra PACKET_FILTER como intermediário
            channel.setTracePeerId("PACKET_FILTER");
            logger.info("HELLO recebido de cliente {} ({})", clientId, peerInfo);

            // Passo 2 - Enviar CHALLENGE
            MessageTCP challenge = channel.handleHello(hello);
            if (challenge == null) {
                logger.error("Falha ao gerar CHALLENGE para {} ({})", clientId, peerInfo);
                return;
            }
            channel.send(challenge);
            logger.debug("CHALLENGE enviado para {} ({})", clientId, peerInfo);

            // Passo 3 - Loop de recebimento de consultas
            while (running && !clientSocket.isClosed()) {
                MessageTCP message = channel.receive();
                if (message == null) {
                    logger.debug("Conexão fechada por {} ({})", clientId, peerInfo);
                    break;
                }

                // Verificar se é envelope cifrado
                if (!channel.isEncryptedEnvelope(message)) {
                    logger.warn("Mensagem não cifrada de {} ({}) - ignorando", clientId, peerInfo);
                    continue;
                }

                // Verificar integridade (HMAC + assinatura RSA)
                if (!channel.verify(message)) {
                    logger.warn("Falha na verificação de mensagem de {} ({})", clientId, peerInfo);
                    continue;
                }

                // Decifrar envelope
                EnvelopeTCP envelope = channel.decryptEnvelope(clientId, message);
                if (envelope == null) {
                    logger.warn("Falha ao decifrar envelope de {} ({})", clientId, peerInfo);
                    continue;
                }

                // Verificar proteção contra replay
                if (!verifyReplayProtection(envelope, usedNonces)) {
                    logger.warn("Replay detectado de {} ({})", clientId, peerInfo);
                    sendError(channel, clientId, "Replay detectado");
                    continue;
                }

                // Rotear baseado no tipo
                switch (envelope.getType()) {
                    case QUERY_REPORT -> handleQueryReport(channel, envelope, clientId, peerInfo);
                    case QUERY_DATA -> handleQueryData(channel, envelope, clientId, peerInfo);
                    default -> {
                        logger.warn("Tipo de mensagem não suportado de {} ({}): {}", clientId, peerInfo, envelope.getType());
                        sendError(channel, clientId, "Tipo de mensagem não suportado: " + envelope.getType());
                    }
                }
            }

        } catch (IOException e) {
            logger.error("Erro de I/O com cliente {}: {}", peerInfo, e.getMessage());
        } finally {
            if (channel != null) {
                if (clientId != null) {
                    channel.clearPeerSession(clientId);
                }
                channel.close();
            }
            logger.info("Conexão encerrada com cliente {}", peerInfo);
        }
    }

    /**
     * Processa QUERY_REPORT do cliente CLI.
     * Payload esperado: { "type": "air-quality|flood|...", "format": "json|html" }
     * JWT no envelope para autenticação.
     */
    private void handleQueryReport(SecureTCPChannel channel, EnvelopeTCP envelope, String clientId, String peerInfo) {
        // Validar JWT
        String token = envelope.getJwtToken();
        if (token == null || !jwt.isValid(token)) {
            logger.warn("JWT inválido ou ausente de {} ({})", clientId, peerInfo);
            sendError(channel, clientId, "Autenticação inválida - JWT ausente ou expirado");
            return;
        }

        String username = jwt.getSensorId(token); // getSensorId retorna o subject (username para clientes)
        logger.info("QUERY_REPORT de usuário '{}' via cliente {} ({})", username, clientId, peerInfo);

        try {
            JsonObject payload = gson.fromJson(envelope.getPayload(), JsonObject.class);
            String reportType = payload.has("type") ? payload.get("type").getAsString() : "air-quality";
            String format = payload.has("format") ? payload.get("format").getAsString() : "json";

            // Buscar dados do DataStore
            List<SensorReading> readings = dataStore.getAll();
            
            if (readings.isEmpty()) {
                logger.warn("Nenhum dado disponível para relatório {} ({})", reportType, peerInfo);
                sendError(channel, clientId, "Nenhum dado disponível");
                return;
            }

            // Gerar relatório no formato solicitado
            String responseData;
            if ("html".equalsIgnoreCase(format)) {
                responseData = reportService.generateReport(reportType, readings);
            } else {
                // Default: JSON
                JsonObject reportJson = reportService.generateReportJson(reportType, readings);
                responseData = gson.toJson(reportJson);
            }

            // Enviar QUERY_RESPONSE
            MessageTCP response = channel.buildEncryptedEnvelope(
                clientId, MessageTypeTCP.QUERY_RESPONSE, responseData
            );
            if (response != null) {
                channel.send(response);
                logger.info("QUERY_RESPONSE ({}) enviado para {} ({}) - {} bytes", 
                        reportType, clientId, peerInfo, responseData.length());
            }

        } catch (Exception e) {
            logger.error("Erro ao processar QUERY_REPORT de {} ({}): {}", clientId, peerInfo, e.getMessage());
            sendError(channel, clientId, "Erro interno ao gerar relatório");
        }
    }

    /**
     * Processa QUERY_DATA do cliente CLI.
     * Payload esperado: { "limit": 100, "offset": 0 }
     * Retorna dados brutos dos sensores em formato JSON.
     */
    private void handleQueryData(SecureTCPChannel channel, EnvelopeTCP envelope, String clientId, String peerInfo) {
        // Validar JWT
        String token = envelope.getJwtToken();
        if (token == null || !jwt.isValid(token)) {
            logger.warn("JWT inválido ou ausente de {} ({})", clientId, peerInfo);
            sendError(channel, clientId, "Autenticação inválida - JWT ausente ou expirado");
            return;
        }

        String username = jwt.getSensorId(token);
        logger.info("QUERY_DATA de usuário '{}' via cliente {} ({})", username, clientId, peerInfo);

        try {
            JsonObject payload = gson.fromJson(envelope.getPayload(), JsonObject.class);
            int limit = payload.has("limit") ? payload.get("limit").getAsInt() : 100;
            int offset = payload.has("offset") ? payload.get("offset").getAsInt() : 0;

            // Buscar dados paginados
            List<SensorReading> readings = dataStore.getAll();
            int total = readings.size();
            
            // Aplicar paginação
            int start = Math.min(offset, total);
            int end = Math.min(offset + limit, total);
            List<SensorReading> paged = readings.subList(start, end);

            // Construir resposta JSON
            JsonObject response = new JsonObject();
            response.addProperty("total", total);
            response.addProperty("offset", offset);
            response.addProperty("limit", limit);
            response.addProperty("count", paged.size());
            response.add("data", gson.toJsonTree(paged.stream().map(r -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("sensorId", r.sensorId());
                obj.addProperty("timestamp", r.timestamp());
                obj.add("data", r.data());
                obj.addProperty("isAlert", r.isAlert());
                if (r.alertType() != null) {
                    obj.addProperty("alertType", r.alertType());
                }
                return obj;
            }).toList()));

            // Enviar QUERY_RESPONSE
            String responseData = gson.toJson(response);
            MessageTCP responseMsg = channel.buildEncryptedEnvelope(
                clientId, MessageTypeTCP.QUERY_RESPONSE, responseData
            );
            if (responseMsg != null) {
                channel.send(responseMsg);
                logger.info("QUERY_RESPONSE (data) enviado para {} ({}) - {} registros", 
                        clientId, peerInfo, paged.size());
            }

        } catch (Exception e) {
            logger.error("Erro ao processar QUERY_DATA de {} ({}): {}", clientId, peerInfo, e.getMessage());
            sendError(channel, clientId, "Erro interno ao buscar dados");
        }
    }

    private boolean verifyReplayProtection(EnvelopeTCP envelope, Set<String> usedNonces) {
        long timestamp = envelope.getTimestamp();
        String nonce = envelope.getNonce();

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > REPLAY_WINDOW_MS) {
            logger.warn("Timestamp fora da janela de replay: {} (diff: {}ms)", timestamp, Math.abs(now - timestamp));
            return false;
        }

        if (usedNonces.contains(nonce)) {
            logger.warn("Nonce já utilizado: {}", nonce);
            return false;
        }

        usedNonces.add(nonce);
        return true;
    }

    private void sendError(SecureTCPChannel channel, String peerId, String reason) {
        JsonObject payload = new JsonObject();
        payload.addProperty("error", reason);

        MessageTCP error = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.ERROR, payload.toString());
        if (error != null) {
            channel.send(error);
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Erro ao fechar ServerSocket: {}", e.getMessage());
        }
        logger.info("[ClientTcpHandler] Parado");
    }

    public boolean isRunning() {
        return running;
    }
}
