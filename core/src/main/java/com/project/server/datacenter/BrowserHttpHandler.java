package com.project.server.datacenter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.project.server.datacenter.db.DataStore;
import com.project.server.datacenter.db.ReportService;
import com.project.tracing.TraceEvent;
import com.project.tracing.TracerFactory;

public class BrowserHttpHandler {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter.BrowserHttpHandler");
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final int port;
    private final DataStore dataStore;
    private final ReportService reportService;
    private final AuthClient authClient;
    private final Map<String, String> sessions;
    private HttpServer server;
    private volatile boolean running;

    public BrowserHttpHandler(int port, DataStore dataStore, ReportService reportService,
                              AuthClient authClient) {
        this.port = port;
        this.dataStore = dataStore;
        this.reportService = reportService;
        this.authClient = authClient;
        this.sessions = new ConcurrentHashMap<>();
        this.running = false;
    }

    public void start(ExecutorService executor) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/browser/login", this::handleLogin);
            server.createContext("/browser/logout", this::handleLogout);
            server.createContext("/browser/status", this::handleStatus);
            server.createContext("/browser/data", this::handleData);
            server.createContext("/browser/reports", this::handleReportsList);
            server.createContext("/browser/report", this::handleReport);
            server.createContext("/browser/alerts", this::handleAlerts);

            server.setExecutor(executor);
            server.start();
            running = true;

            logger.info("[BrowserHttpHandler] Iniciado na porta {}", port);

        } catch (IOException e) {
            logger.error("Erro ao iniciar BrowserHttpHandler na porta {}: {}", port, e.getMessage());
        }
    }

    // ==================== SESSION MANAGEMENT ====================

    private String createSession(String username) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, username);
        return sessionId;
    }

    private String getSessionCookie(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;

        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "session".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private String getSessionUsername(HttpExchange exchange) {
        String sessionId = getSessionCookie(exchange);
        return sessionId != null ? sessions.get(sessionId) : null;
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        return getSessionUsername(exchange) != null;
    }

    private void setSessionCookie(HttpExchange exchange, String sessionId) {
        exchange.getResponseHeaders().add("Set-Cookie",
            "session=" + sessionId + "; Path=/browser; HttpOnly");
    }

    private void clearSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie",
            "session=; Path=/browser; HttpOnly; Max-Age=0");
    }

    private boolean checkAuth(HttpExchange exchange) throws IOException {
        if (!isAuthenticated(exchange)) {
            redirect(exchange, "/browser/login");
            return false;
        }
        return true;
    }

    // ==================== HANDLERS ====================

    private void handleLogin(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            sendHtml(exchange, 200, renderLoginPage(null));
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            
            String remoteAddress = exchange.getRemoteAddress() != null 
                ? exchange.getRemoteAddress().toString() 
                : "unknown";
            TracerFactory.getTracer().trace(TraceEvent.create(
                "DATACENTER",
                "HTTP",
                "RECEIVE",
                remoteAddress,
                "/browser/login",
                "[CREDENTIALS]",
                null,
                null
            ));
            
            Map<String, String> params = parseFormData(body);

            String username = params.get("username");
            String password = params.get("password");

            String token = authClient.authenticate(username, password);
            if (token == null) {
                sendHtml(exchange, 401, renderLoginPage("Credenciais inválidas"));
                return;
            }

            String sessionId = createSession(username);
            setSessionCookie(exchange, sessionId);
            redirect(exchange, "/browser/status");
            logger.info("Usuário '{}' autenticado via browser", username);
            return;
        }

        sendHtml(exchange, 405, "Método não permitido");
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String sessionId = getSessionCookie(exchange);
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
        clearSessionCookie(exchange);
        redirect(exchange, "/browser/login");
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        String username = getSessionUsername(exchange);
        sendHtml(exchange, 200, renderStatusPage(username));
    }

    private void handleData(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        Map<String, String> queryParams = parseQueryParams(exchange);
        int page = parseIntParam(queryParams, "page", 1);
        int limit = parseIntParam(queryParams, "limit", DEFAULT_PAGE_SIZE);
        int offset = (page - 1) * limit;

        int totalCount = dataStore.getCount();
        int totalPages = (int) Math.ceil((double) totalCount / limit);

        String html = reportService.generateDataTable(
            dataStore.getAll(offset, limit),
            page, totalPages, limit, totalCount, "/browser/data"
        );
        sendHtml(exchange, 200, wrapInLayout("Dados", html));
    }

    private void handleReportsList(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        List<String> reports = reportService.getAvailableReports();
        sendHtml(exchange, 200, renderReportsListPage(reports));
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        String reportType = parts.length >= 4 ? parts[3] : "pollution";

        String reportHtml = reportService.generateReport(reportType, dataStore.getAll());
        sendHtml(exchange, 200, wrapInLayoutNoTitle("Relatório", reportHtml));
    }

    private void handleAlerts(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        Map<String, String> queryParams = parseQueryParams(exchange);
        int page = parseIntParam(queryParams, "page", 1);
        int limit = parseIntParam(queryParams, "limit", DEFAULT_PAGE_SIZE);
        int offset = (page - 1) * limit;

        int totalCount = dataStore.getAlertCount();
        int totalPages = (int) Math.ceil((double) totalCount / limit);

        String html = reportService.generateDataTable(
            dataStore.getAlerts(offset, limit),
            page, totalPages, limit, totalCount, "/browser/alerts"
        );
        sendHtml(exchange, 200, wrapInLayout("Alertas", html));
    }

    // ==================== HTML RENDERING ====================

    private String renderLoginPage(String error) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<title>Login - Datacenter</title>");
        html.append("<style>");
        html.append("body { font-family: sans-serif; max-width: 400px; margin: 50px auto; padding: 20px; }");
        html.append("input { width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; }");
        html.append("button { width: 100%; padding: 10px; background: #007bff; color: white; border: none; cursor: pointer; }");
        html.append("button:hover { background: #0056b3; }");
        html.append(".error { color: red; margin-bottom: 10px; }");
        html.append("</style>");
        html.append("</head><body>");
        html.append("<h1>Login - Datacenter</h1>");
        if (error != null) {
            html.append("<div class='error'>").append(error).append("</div>");
        }
        html.append("<form method='POST' action='/browser/login'>");
        html.append("<input type='text' name='username' placeholder='Usuário' required>");
        html.append("<input type='password' name='password' placeholder='Senha' required>");
        html.append("<button type='submit'>Entrar</button>");
        html.append("</form>");
        html.append("<p style='margin-top:20px;color:#666;font-size:12px;'>Usuários de teste: admin/admin123, cliente/cliente123</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private String renderStatusPage(String username) {
        StringBuilder html = new StringBuilder();
        html.append(getHeader("Status"));
        html.append("<h1>Status do Datacenter</h1>");
        html.append(getAutoRefreshToggle());
        html.append("<p>Usuário: <strong>").append(username).append("</strong></p>");
        html.append("<div id='status-content'>");
        html.append("<table style='border-collapse:collapse;'>");
        html.append("<tr><td style='padding:5px;border:1px solid #ccc;'>Leituras</td>");
        html.append("<td style='padding:5px;border:1px solid #ccc;' id='count-leituras'>").append(dataStore.getCount()).append("</td></tr>");
        html.append("<tr><td style='padding:5px;border:1px solid #ccc;'>Alertas</td>");
        html.append("<td style='padding:5px;border:1px solid #ccc;' id='count-alertas'>").append(dataStore.getAlertCount()).append("</td></tr>");
        html.append("<tr><td style='padding:5px;border:1px solid #ccc;'>Sensores</td>");
        html.append("<td style='padding:5px;border:1px solid #ccc;' id='count-sensores'>").append(dataStore.getSensorIds().size()).append("</td></tr>");
        html.append("</table>");
        html.append("</div>");
        html.append(getNavigation());
        html.append(getAutoRefreshScript());
        html.append(getFooter());
        return html.toString();
    }

    private String renderReportsListPage(List<String> reports) {
        StringBuilder html = new StringBuilder();
        html.append(getHeader("Relatórios"));
        html.append("<h1>Relatórios Disponíveis</h1>");
        html.append("<ul>");
        for (String report : reports) {
            html.append("<li><a href='/browser/report/").append(report).append("'>")
                .append(formatReportName(report)).append("</a></li>");
        }
        html.append("</ul>");
        html.append(getNavigation());
        html.append(getFooter());
        return html.toString();
    }

    private String formatReportName(String report) {
        return switch (report) {
            case "pollution" -> "Poluição";
            case "flood" -> "Enchente";
            case "noise" -> "Ruído";
            case "uv" -> "Índice UV";
            case "air-quality" -> "Qualidade do Ar";
            default -> report;
        };
    }

    private String wrapInLayout(String title, String content) {
        return getHeader(title) + getNavigation() + "<h1>" + title + "</h1>" + content + getFooter();
    }

    private String wrapInLayoutNoTitle(String title, String content) {
        return getHeader(title) + getNavigation() + content + getFooter();
    }

    private String getHeader(String title) {
        return "<!DOCTYPE html><html><head><title>" + title + " - Datacenter</title>" +
               "<style>body { font-family: sans-serif; margin: 20px; } " +
               "nav { margin-bottom: 20px; padding-bottom: 10px; border-bottom: 1px solid #ccc; } " +
               "nav a { margin-right: 15px; } " +
               "table { border-collapse: collapse; margin: 10px 0; } " +
               "th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }</style></head><body>";
    }

    private String getNavigation() {
        return "<nav>" +
               "<a href='/browser/status'>Status</a>" +
               "<a href='/browser/data'>Dados</a>" +
               "<a href='/browser/reports'>Relatórios</a>" +
               "<a href='/browser/alerts'>Alertas</a>" +
               "<a href='/browser/logout' style='color:red;'>Sair</a>" +
               "</nav>";
    }

    private String getFooter() {
        return "</body></html>";
    }

    private String getAutoRefreshToggle() {
        return "<div style='margin-bottom:15px;display:flex;align-items:center;gap:8px;'>" +
               "<span id='refresh-indicator' style='width:10px;height:10px;border-radius:50%;background:#4CAF50;'></span>" +
               "<label><input type='checkbox' id='auto-refresh' checked> Auto-refresh (5s)</label>" +
               "</div>";
    }

    private String getAutoRefreshScript() {
        return "<script>" +
               "(function(){" +
               "var timer=null,cb=document.getElementById('auto-refresh'),ind=document.getElementById('refresh-indicator');" +
               "function upd(on){ind.style.background=on?'#4CAF50':'#ccc';}" +
               "function refresh(){fetch(location.href).then(r=>r.text()).then(html=>{" +
               "var doc=new DOMParser().parseFromString(html,'text/html');" +
               "['count-leituras','count-alertas','count-sensores'].forEach(id=>{" +
               "var el=doc.getElementById(id);if(el)document.getElementById(id).textContent=el.textContent;" +
               "});}).catch(e=>console.error('Refresh failed:',e));}" +
               "function start(){if(timer)clearInterval(timer);timer=setInterval(refresh,5000);upd(true);}" +
               "function stop(){if(timer){clearInterval(timer);timer=null;}upd(false);}" +
               "cb.onchange=function(){this.checked?start():stop();};" +
               "start();" +
               "})();" +
               "</script>";
    }

    // ==================== HELPERS ====================

    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    params.put(
                        URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                    );
                }
            }
        }
        return params;
    }

    private int parseIntParam(Map<String, String> params, String key, int defaultValue) {
        String value = params.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(
                    URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params;
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        String remoteAddress = exchange.getRemoteAddress() != null 
            ? exchange.getRemoteAddress().toString() 
            : "unknown";
        
        TracerFactory.getTracer().trace(TraceEvent.create(
            "DATACENTER",
            "HTTP",
            "SEND",
            remoteAddress,
            exchange.getRequestURI().getPath() + " [" + status + "]",
            html.length() > 500 ? html.substring(0, 500) + "..." : html,
            null,
            null
        ));
        
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    public void stop() {
        running = false;
        sessions.clear();
        if (server != null) {
            server.stop(0);
        }
        logger.info("[BrowserHttpHandler] Parado");
    }

    public boolean isRunning() {
        return running;
    }
}
