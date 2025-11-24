package com.project.datacenter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.model.DadosAmbientais;
import com.project.model.Relatorio;
import com.project.model.TipoRelatorio;
import com.project.security.JWTManager;
import com.project.security.RateLimiter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class ServidorHTTP {
    private final int porta;
    private final BancoDados bancoDados;
    private HttpServer server;
    private volatile boolean executando;
    private final Gson gson;  // Instância do Gson para serialização JSON
    private final RateLimiter rateLimiter;  // Controle de rate limiting para login
    
    // Credenciais de usuários (em produção, usar banco de dados com hash bcrypt/argon2)
    private static final Map<String, String> USUARIOS = Map.of(
        "admin", "admin123",
        "client1", "senha123",
        "client2", "senha456"
    );

    public ServidorHTTP(int porta, BancoDados bancoDados) {
        this.porta = porta;
        this.bancoDados = bancoDados;
        this.executando = false;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()  // JSON formatado para melhor legibilidade
            .create();
        this.rateLimiter = new RateLimiter(3, 15 * 60 * 1000);  // 3 tentativas, 15 minutos
    }

    public void iniciar() {
        try {
            server = HttpServer.create(new InetSocketAddress(porta), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            
            // Registrar endpoints
            server.createContext("/api/login", new LoginHandler());  // Endpoint de autenticação
            server.createContext("/api/relatorios/iqa", new IQAHandler());
            server.createContext("/api/relatorios/tendencias", new TendenciasHandler());
            server.createContext("/api/relatorios/microclima", new MicroclimaHandler());
            server.createContext("/api/relatorios/enchentes", new EnchetesHandler());
            server.createContext("/api/relatorios/trafego", new TrafegoHandler());
            server.createContext("/api/status", new StatusHandler());
            
            server.start();
            executando = true;

            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║          SERVIDOR HTTP REST API INICIADO                       ║");
            System.out.println("╠════════════════════════════════════════════════════════════════╣");
            System.out.printf("║  Porta: %-55d║%n", porta);
            System.out.println("║  Base URL: http://localhost:" + porta + "/api                      ║");
            System.out.println("║                                                                ║");
            System.out.println("║  Endpoints disponíveis:                                        ║");
            System.out.println("║    POST /api/login                                             ║");
            System.out.println("║    GET /api/relatorios/iqa                                     ║");
            System.out.println("║    GET /api/relatorios/tendencias                              ║");
            System.out.println("║    GET /api/relatorios/microclima                              ║");
            System.out.println("║    GET /api/relatorios/enchentes                               ║");
            System.out.println("║    GET /api/relatorios/trafego                                 ║");
            System.out.println("║    GET /api/status                                             ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");

        } catch (IOException e) {
            System.err.println("[ServidorHTTP] ❌ Erro ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void parar() {
        executando = false;
        if (server != null) {
            server.stop(0);
            System.out.println("[ServidorHTTP] 🛑 Servidor HTTP encerrado");
        }
    }

    public boolean isExecutando() {
        return executando;
    }

    // ═══════════════════════════════════════════════════════════════
    // MÉTODOS AUXILIARES
    // ═══════════════════════════════════════════════════════════════

    private boolean validarToken(String token) {
        return token != null && JWTManager.validarToken(token);
    }

    private String extrairToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) {
                try {
                    String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    // Ignorar parâmetros inválidos
                }
            }
        }
        return params;
    }

    private void enviarResposta(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String gerarErroJSON(String mensagem) {
        Map<String, Object> erro = new HashMap<>();
        erro.put("erro", mensagem);
        erro.put("timestamp", System.currentTimeMillis());
        return gson.toJson(erro);
    }

    private String relatorioParaJSON(Relatorio rel) {
        // Usar Gson para serialização automática do objeto Relatorio
        return gson.toJson(rel);
    }

    private String gerarToken(String usuario) {
        // Claims customizados para incluir no JWT
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", usuario);
        claims.put("admin", "admin".equals(usuario)); // Apenas 'admin' tem privilégios de admin
        
        return JWTManager.gerarToken(usuario, claims);
    }

    private String extrairCampoJSON(String json, String campo) {
        String busca = "\"" + campo + "\":\"";
        int inicio = json.indexOf(busca);
        if (inicio == -1) return null;
        inicio += busca.length();
        int fim = json.indexOf("\"", inicio);
        if (fim == -1) return null;
        return json.substring(inicio, fim);
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLERS DOS ENDPOINTS
    // ═══════════════════════════════════════════════════════════════

    class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Verificar método HTTP
            if (!"POST".equals(exchange.getRequestMethod())) {
                enviarResposta(exchange, 405, gerarErroJSON("Método não permitido. Use POST."));
                return;
            }
            
            try {
                // Extrair IP do cliente
                String clienteIP = exchange.getRemoteAddress().getAddress().getHostAddress();
                
                // Verificar se está bloqueado
                if (rateLimiter.estaBloqueado(clienteIP)) {
                    long tempoRestante = rateLimiter.getTempoRestanteBloqueio(clienteIP);
                    String mensagem = String.format("Conta temporariamente bloqueada. Tente novamente em %d segundos.", tempoRestante);
                    enviarResposta(exchange, 429, gerarErroJSON(mensagem));
                    System.out.println("[ServidorHTTP] 🚫 Tentativa de login bloqueada para IP: " + clienteIP);
                    return;
                }
                
                // Ler body JSON
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                
                if (body.isEmpty()) {
                    enviarResposta(exchange, 400, gerarErroJSON("Body vazio"));
                    return;
                }
                
                System.out.println("[ServidorHTTP] 🔐 Tentativa de login do IP: " + clienteIP);
                
                // Parse JSON usando Gson
                @SuppressWarnings("unchecked")
                Map<String, String> credenciais = gson.fromJson(body, Map.class);
                String usuario = credenciais.get("usuario");
                String senha = credenciais.get("senha");
                
                if (usuario == null || senha == null) {
                    enviarResposta(exchange, 400, gerarErroJSON("Campos usuario e senha são obrigatórios"));
                    return;
                }
                
                // Validar credenciais
                String senhaEsperada = USUARIOS.get(usuario);
                if (senhaEsperada != null && senhaEsperada.equals(senha)) {
                    // Login bem-sucedido - resetar rate limiter
                    rateLimiter.resetar(clienteIP);
                    
                    String token = gerarToken(usuario);
                    Map<String, String> resposta = new HashMap<>();
                    resposta.put("token", token);
                    resposta.put("usuario", usuario);
                    enviarResposta(exchange, 200, gson.toJson(resposta));
                    System.out.println("[ServidorHTTP] ✅ Login bem-sucedido: " + usuario + " (IP: " + clienteIP + ")");
                } else {
                    // Login falhou - registrar tentativa
                    boolean bloqueado = rateLimiter.registrarTentativaFalha(clienteIP);
                    
                    if (bloqueado) {
                        // Foi bloqueado agora
                        long tempoRestante = rateLimiter.getTempoRestanteBloqueio(clienteIP);
                        String mensagem = String.format("Máximo de tentativas excedido. Conta bloqueada por %d minutos.", tempoRestante / 60);
                        enviarResposta(exchange, 429, gerarErroJSON(mensagem));
                        System.out.println("[ServidorHTTP] 🔒 IP bloqueado por excesso de tentativas: " + clienteIP);
                    } else {
                        int tentativasRestantes = rateLimiter.getTentativasRestantes(clienteIP);
                        String mensagem = String.format("Credenciais inválidas. %d tentativa(s) restante(s).", tentativasRestantes);
                        enviarResposta(exchange, 401, gerarErroJSON(mensagem));
                        System.out.printf("[ServidorHTTP] ❌ Login falhou para: %s (IP: %s, tentativas restantes: %d)%n", 
                            usuario, clienteIP, tentativasRestantes);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("[ServidorHTTP] ❌ Erro no login: " + e.getMessage());
                e.printStackTrace();
                enviarResposta(exchange, 500, gerarErroJSON("Erro interno no servidor"));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLERS DOS RELATÓRIOS
    // ═══════════════════════════════════════════════════════════════

    class IQAHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Validar autenticação
                String token = extrairToken(exchange);
                if (!validarToken(token)) {
                    enviarResposta(exchange, 401, gerarErroJSON("Token inválido ou ausente"));
                    return;
                }

                // Parse parâmetros
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                long inicio = Long.parseLong(params.getOrDefault("inicio", "0"));
                long fim = Long.parseLong(params.getOrDefault("fim", String.valueOf(System.currentTimeMillis())));
                String sensorId = params.get("sensor");

                System.out.printf("[ServidorHTTP] 📊 Consultando IQA: %d - %d (sensor: %s)%n", inicio, fim, sensorId);

                // Consultar banco
                List<DadosAmbientais> dados;
                if (sensorId != null) {
                    dados = bancoDados.consultarPorSensor(sensorId, inicio, fim);
                } else {
                    dados = bancoDados.consultarPorPeriodo(inicio, fim);
                }

                if (dados.isEmpty()) {
                    enviarResposta(exchange, 404, gerarErroJSON("Nenhum dado encontrado para o período"));
                    return;
                }

                // Gerar relatório
                Relatorio relatorio = Relatorio.gerar(TipoRelatorio.INDICE_QUALIDADE_AR, dados);
                String json = relatorioParaJSON(relatorio);

                enviarResposta(exchange, 200, json);
                System.out.println("[ServidorHTTP] ✅ Relatório IQA enviado (" + dados.size() + " leituras)");

            } catch (NumberFormatException e) {
                enviarResposta(exchange, 400, gerarErroJSON("Parâmetros inválidos"));
            } catch (Exception e) {
                System.err.println("[ServidorHTTP] ❌ Erro ao processar IQA: " + e.getMessage());
                e.printStackTrace();
                enviarResposta(exchange, 500, gerarErroJSON("Erro interno: " + e.getMessage()));
            }
        }
    }

    class TendenciasHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String token = extrairToken(exchange);
                if (!validarToken(token)) {
                    enviarResposta(exchange, 401, gerarErroJSON("Token inválido ou ausente"));
                    return;
                }

                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                long inicio = Long.parseLong(params.getOrDefault("inicio", "0"));
                long fim = Long.parseLong(params.getOrDefault("fim", String.valueOf(System.currentTimeMillis())));

                System.out.printf("[ServidorHTTP] 📈 Consultando Tendências: %d - %d%n", inicio, fim);

                List<DadosAmbientais> dados = bancoDados.consultarPorPeriodo(inicio, fim);
                if (dados.isEmpty()) {
                    enviarResposta(exchange, 404, gerarErroJSON("Nenhum dado encontrado"));
                    return;
                }

                Relatorio relatorio = Relatorio.gerar(TipoRelatorio.TENDENCIAS_POLUICAO, dados);
                enviarResposta(exchange, 200, relatorioParaJSON(relatorio));
                System.out.println("[ServidorHTTP] ✅ Relatório Tendências enviado");

            } catch (Exception e) {
                System.err.println("[ServidorHTTP] ❌ Erro: " + e.getMessage());
                enviarResposta(exchange, 500, gerarErroJSON("Erro interno"));
            }
        }
    }

    class MicroclimaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String token = extrairToken(exchange);
                if (!validarToken(token)) {
                    enviarResposta(exchange, 401, gerarErroJSON("Token inválido ou ausente"));
                    return;
                }

                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String localizacao = params.get("localizacao");
                long inicio = Long.parseLong(params.getOrDefault("inicio", "0"));
                long fim = Long.parseLong(params.getOrDefault("fim", String.valueOf(System.currentTimeMillis())));

                if (localizacao == null) {
                    enviarResposta(exchange, 400, gerarErroJSON("Parâmetro 'localizacao' obrigatório"));
                    return;
                }

                System.out.printf("[ServidorHTTP] 🌡️  Consultando Microclima: %s (%d - %d)%n", localizacao, inicio, fim);

                List<DadosAmbientais> dados = bancoDados.consultarPorLocalizacao(localizacao, inicio, fim);
                if (dados.isEmpty()) {
                    enviarResposta(exchange, 404, gerarErroJSON("Nenhum dado encontrado para esta localização"));
                    return;
                }

                Relatorio relatorio = Relatorio.gerar(TipoRelatorio.ANALISE_MICROCLIMA, dados);
                enviarResposta(exchange, 200, relatorioParaJSON(relatorio));
                System.out.println("[ServidorHTTP] ✅ Relatório Microclima enviado");

            } catch (Exception e) {
                System.err.println("[ServidorHTTP] ❌ Erro: " + e.getMessage());
                enviarResposta(exchange, 500, gerarErroJSON("Erro interno"));
            }
        }
    }

    class EnchetesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String token = extrairToken(exchange);
                if (!validarToken(token)) {
                    enviarResposta(exchange, 401, gerarErroJSON("Token inválido ou ausente"));
                    return;
                }

                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String localizacao = params.get("localizacao");

                if (localizacao == null) {
                    enviarResposta(exchange, 400, gerarErroJSON("Parâmetro 'localizacao' obrigatório"));
                    return;
                }

                System.out.printf("[ServidorHTTP] 🌊 Consultando Enchentes: %s%n", localizacao);

                // Últimas 24 horas
                long fim = System.currentTimeMillis();
                long inicio = fim - (24 * 60 * 60 * 1000);

                List<DadosAmbientais> dados = bancoDados.consultarPorLocalizacao(localizacao, inicio, fim);
                if (dados.isEmpty()) {
                    enviarResposta(exchange, 404, gerarErroJSON("Nenhum dado encontrado"));
                    return;
                }

                Relatorio relatorio = Relatorio.gerar(TipoRelatorio.ALERTAS_ENCHENTE, dados);
                enviarResposta(exchange, 200, relatorioParaJSON(relatorio));
                System.out.println("[ServidorHTTP] ✅ Relatório Enchentes enviado");

            } catch (Exception e) {
                System.err.println("[ServidorHTTP] ❌ Erro: " + e.getMessage());
                enviarResposta(exchange, 500, gerarErroJSON("Erro interno"));
            }
        }
    }

    class TrafegoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String token = extrairToken(exchange);
                if (!validarToken(token)) {
                    enviarResposta(exchange, 401, gerarErroJSON("Token inválido ou ausente"));
                    return;
                }

                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String localizacao = params.get("localizacao");
                long inicio = Long.parseLong(params.getOrDefault("inicio", "0"));
                long fim = Long.parseLong(params.getOrDefault("fim", String.valueOf(System.currentTimeMillis())));

                if (localizacao == null) {
                    enviarResposta(exchange, 400, gerarErroJSON("Parâmetro 'localizacao' obrigatório"));
                    return;
                }

                System.out.printf("[ServidorHTTP] 🚦 Consultando Tráfego: %s (%d - %d)%n", localizacao, inicio, fim);

                List<DadosAmbientais> dados = bancoDados.consultarPorLocalizacao(localizacao, inicio, fim);
                if (dados.isEmpty()) {
                    enviarResposta(exchange, 404, gerarErroJSON("Nenhum dado encontrado"));
                    return;
                }

                Relatorio relatorio = Relatorio.gerar(TipoRelatorio.RECOMENDACOES_TRAFEGO, dados);
                enviarResposta(exchange, 200, relatorioParaJSON(relatorio));
                System.out.println("[ServidorHTTP] ✅ Relatório Tráfego enviado");

            } catch (Exception e) {
                System.err.println("[ServidorHTTP] ❌ Erro: " + e.getMessage());
                enviarResposta(exchange, 500, gerarErroJSON("Erro interno"));
            }
        }
    }

    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                int totalSensores = bancoDados.contarPorSensor();
                int totalLeituras = bancoDados.contarLeituras();

                String json = String.format(
                    "{\"status\":\"online\",\"totalSensores\":%d,\"totalLeituras\":%d,\"timestamp\":%d}",
                    totalSensores, totalLeituras, System.currentTimeMillis()
                );

                enviarResposta(exchange, 200, json);
            } catch (Exception e) {
                enviarResposta(exchange, 500, gerarErroJSON("Erro ao obter status"));
            }
        }
    }
}
