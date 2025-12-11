package com.project.server.datacenter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.project.auth.JWT;
import com.project.crypto.KeyManager;
import com.project.message.http.MessageHTTP;
import com.project.message.http.MessageTypeHTTP;
import com.project.network.SecureHTTPHelper;
import com.project.server.auth.ServerAuth;
import com.project.server.datacenter.db.DataStore;
import com.project.server.datacenter.db.ReportService;
import com.project.tracing.TraceEvent;
import com.project.tracing.TracerFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter.HttpHandler");
    private static final Gson gson = new Gson();

    private final int port;
    private final DataStore dataStore;
    private final ReportService reportService;
    private final SecureHTTPHelper secureHelper;
    private final JWT jwt;
    private HttpServer server;
    private volatile boolean running;
    
    private AuthClient authClient;

    public HttpHandler(int port, DataStore dataStore, ReportService reportService, 
                       KeyManager keyManager) {
        this.port = port;
        this.dataStore = dataStore;
        this.reportService = reportService;
        this.jwt = new JWT(ServerAuth.JWT_SECRET, "AuthServer");
        this.secureHelper = new SecureHTTPHelper("DATACENTER", keyManager, jwt);
        this.running = false;
    }
    
    public void setAuthClient(AuthClient authClient) {
        this.authClient = authClient;
    }

    public void start(ExecutorService executor) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Endpoints de autenticação
            server.createContext("/handshake", this::handleHandshake);
            server.createContext("/auth", this::handleAuth);
            
            // Endpoints de dados (requerem autenticação)
            server.createContext("/data", this::handleDataRequest);
            server.createContext("/reports", this::handleReportsListRequest);
            server.createContext("/report", this::handleReportRequest);
            server.createContext("/alerts", this::handleAlertsRequest);
            server.createContext("/status", this::handleStatusRequest);
            
            server.setExecutor(executor);
            server.start();
            running = true;
            
            logger.info("[HttpHandler] Iniciado na porta {}", port);
            
        } catch (IOException e) {
            logger.error("Erro ao iniciar HttpHandler na porta {}: {}", port, e.getMessage());
        }
    }

    // ==================== HANDSHAKE ====================

    private void handleHandshake(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Método não permitido");
            return;
        }

        String body = readRequestBody(exchange);
        MessageHTTP hello = MessageHTTP.fromJson(body);
        String clientId = hello.getClientId();
        
        logger.debug("HELLO recebido de '{}'", clientId);

        MessageHTTP challenge = secureHelper.handleHello(hello);
        if (challenge == null) {
            sendJsonResponse(exchange, 500, errorJson("Falha no handshake"));
            return;
        }

        sendJsonResponse(exchange, 200, challenge.toJson());
        logger.debug("CHALLENGE enviado para '{}'", clientId);
    }

    // ==================== AUTH ====================

    private void handleAuth(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Método não permitido");
            return;
        }

        String body = readRequestBody(exchange);
        MessageHTTP request = MessageHTTP.fromJson(body);
        String clientId = request.getClientId();

        logger.info("AUTH recebido de '{}'", clientId);

        // Passo 1 - Verificar HMAC + assinatura (sem JWT)
        if (!secureHelper.verifyWithoutJwt(request)) {
            logger.warn("Verificação falhou para '{}'", clientId);
            sendAuthFail(exchange, clientId, "Verificação falhou");
            return;
        }

        // Passo 2 - Decifrar credenciais
        String payload = secureHelper.decrypt(clientId, request);
        if (payload == null) {
            sendAuthFail(exchange, clientId, "Falha na decifração");
            return;
        }

        JsonObject creds = gson.fromJson(payload, JsonObject.class);
        String username = creds.get("username").getAsString();
        String password = creds.get("password").getAsString();

        // Passo 3 - Delegar autenticação ao AuthServer via TCP
        if (authClient == null) {
            logger.error("AuthClient não configurado");
            sendAuthFail(exchange, clientId, "Serviço de autenticação indisponível");
            return;
        }

        String token = authClient.authenticate(username, password);
        if (token == null) {
            logger.warn("Autenticação rejeitada pelo AuthServer para '{}' - username: {}", clientId, username);
            sendAuthFail(exchange, clientId, "Credenciais inválidas");
            return;
        }

        // Passo 4 - Enviar AUTH_OK com token do AuthServer
        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("token", token);
        responsePayload.addProperty("username", username);

        MessageHTTP response = secureHelper.buildEncrypted(clientId, MessageTypeHTTP.AUTH_OK, responsePayload.toString());
        if (response == null) {
            sendAuthFail(exchange, clientId, "Erro interno");
            return;
        }

        sendJsonResponse(exchange, 200, response.toJson());
        logger.info("Cliente '{}' autenticado como '{}'", clientId, username);
    }

    private void sendAuthFail(HttpExchange exchange, String clientId, String reason) throws IOException {
        if (secureHelper.hasSession(clientId)) {
            JsonObject payload = new JsonObject();
            payload.addProperty("error", reason);
            MessageHTTP response = secureHelper.buildEncrypted(clientId, MessageTypeHTTP.AUTH_FAIL, payload.toString());
            if (response != null) {
                sendJsonResponse(exchange, 401, response.toJson());
                return;
            }
        }
        sendJsonResponse(exchange, 401, errorJson(reason));
    }

    // ==================== DATA ENDPOINTS (ENCRYPTED) ====================

    private void handleDataRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Use POST com corpo cifrado");
            return;
        }

        String body = readRequestBody(exchange);
        MessageHTTP request = MessageHTTP.fromJson(body);
        String clientId = request.getClientId();

        if (!secureHelper.verify(request)) {
            sendError(exchange, clientId, "Verificação falhou", 401);
            return;
        }

        String data = gson.toJson(dataStore.getAll());
        MessageHTTP response = secureHelper.buildEncrypted(clientId, MessageTypeHTTP.RESPONSE, data);
        sendJsonResponse(exchange, 200, response.toJson());
    }

    private void handleReportsListRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Use POST com corpo cifrado");
            return;
        }

        String body = readRequestBody(exchange);
        MessageHTTP request = MessageHTTP.fromJson(body);
        String clientId = request.getClientId();

        if (!secureHelper.verify(request)) {
            sendError(exchange, clientId, "Verificação falhou", 401);
            return;
        }

        List<String> reports = reportService.getAvailableReports();
        JsonObject payload = new JsonObject();
        payload.add("reports", gson.toJsonTree(reports));

        MessageHTTP response = secureHelper.buildEncrypted(clientId, MessageTypeHTTP.RESPONSE, payload.toString());
        sendJsonResponse(exchange, 200, response.toJson());
    }

    private void handleReportRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Use POST com corpo cifrado");
            return;
        }

        String body = readRequestBody(exchange);
        MessageHTTP request = MessageHTTP.fromJson(body);
        String clientId = request.getClientId();

        if (!secureHelper.verify(request)) {
            sendError(exchange, clientId, "Verificação falhou", 401);
            return;
        }

        // Decifrar payload para obter tipo de relatório e formato
        String payload = secureHelper.decrypt(clientId, request);
        if (payload == null) {
            sendError(exchange, clientId, "Falha na decifração", 400);
            return;
        }

        JsonObject queryData = gson.fromJson(payload, JsonObject.class);
        String reportType = queryData.has("type") ? queryData.get("type").getAsString() : "pollution";
        String format = queryData.has("format") ? queryData.get("format").getAsString() : "json";

        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("type", reportType);

        if ("html".equals(format)) {
            // Formato HTML para browser
            String reportHtml = reportService.generateReport(reportType, dataStore.getAll());
            responsePayload.addProperty("content", reportHtml);
        } else {
            // Formato JSON para CLI (default)
            JsonObject reportData = reportService.generateReportJson(reportType, dataStore.getAll());
            responsePayload.add("data", reportData);
        }

        MessageHTTP response = secureHelper.buildEncrypted(clientId, MessageTypeHTTP.RESPONSE, responsePayload.toString());
        sendJsonResponse(exchange, 200, response.toJson());
        
        logger.info("Relatório '{}' ({}) enviado para '{}'", reportType, format, clientId);
    }

    private void handleAlertsRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Use POST com corpo cifrado");
            return;
        }

        String body = readRequestBody(exchange);
        MessageHTTP request = MessageHTTP.fromJson(body);
        String clientId = request.getClientId();

        if (!secureHelper.verify(request)) {
            sendError(exchange, clientId, "Verificação falhou", 401);
            return;
        }

        String data = gson.toJson(dataStore.getAlerts());
        MessageHTTP response = secureHelper.buildEncrypted(clientId, MessageTypeHTTP.RESPONSE, data);
        sendJsonResponse(exchange, 200, response.toJson());
    }

    private void handleStatusRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Use POST com corpo cifrado");
            return;
        }

        String body = readRequestBody(exchange);
        MessageHTTP request = MessageHTTP.fromJson(body);
        String clientId = request.getClientId();

        if (!secureHelper.verify(request)) {
            sendError(exchange, clientId, "Verificação falhou", 401);
            return;
        }

        JsonObject status = new JsonObject();
        status.addProperty("running", running);
        status.addProperty("httpPort", port);
        status.addProperty("totalReadings", dataStore.getCount());
        status.addProperty("totalAlerts", dataStore.getAlertCount());
        status.addProperty("sensors", dataStore.getSensorIds().size());

        MessageHTTP response = secureHelper.buildEncrypted(clientId, MessageTypeHTTP.RESPONSE, status.toString());
        sendJsonResponse(exchange, 200, response.toJson());
    }

    // ==================== HELPERS ====================

    private String readRequestBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String remoteAddress = exchange.getRemoteAddress() != null 
            ? exchange.getRemoteAddress().toString() 
            : "unknown";
        
        TracerFactory.getTracer().trace(TraceEvent.create(
            "DATACENTER",
            "HTTP",
            "RECEIVE",
            remoteAddress,
            exchange.getRequestURI().getPath(),
            body,
            null,
            null
        ));
        
        return body;
    }

    private void sendError(HttpExchange exchange, String clientId, String reason, int statusCode) throws IOException {
        if (secureHelper.hasSession(clientId)) {
            JsonObject payload = new JsonObject();
            payload.addProperty("error", reason);
            MessageHTTP response = secureHelper.buildEncrypted(clientId, MessageTypeHTTP.ERROR, payload.toString());
            if (response != null) {
                sendJsonResponse(exchange, statusCode, response.toJson());
                return;
            }
        }
        sendJsonResponse(exchange, statusCode, errorJson(reason));
    }

    private String errorJson(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error.toString();
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        String remoteAddress = exchange.getRemoteAddress() != null 
            ? exchange.getRemoteAddress().toString() 
            : "unknown";
        
        TracerFactory.getTracer().trace(TraceEvent.create(
            "DATACENTER",
            "HTTP",
            "SEND",
            remoteAddress,
            exchange.getRequestURI().getPath() + " [" + statusCode + "]",
            json,
            null,
            null
        ));
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void stop() {
        running = false;
        if (server != null) {
            server.stop(0);
        }
        logger.info("[HttpHandler] Parado");
    }

    public boolean isRunning() {
        return running;
    }
}
