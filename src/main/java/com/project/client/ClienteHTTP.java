package com.project.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ClienteHTTP {

    private static final Logger logger = LoggerFactory.getLogger(ClienteHTTP.class);

    private String urlBase;
    private String token;
    private int connectTimeout; // Timeout para estabelecer conexão
    private int readTimeout;    // Timeout para ler resposta
    private static final Gson gson = new Gson();

    public ClienteHTTP(String protocol, String host, int porta, String token, int connectTimeout, int readTimeout) {
        this.urlBase = protocol + "://" + host + ":" + porta + "/api";
        this.token = token;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public static String autenticar(String protocol, String host, int porta, String usuario, String senha,
                                      int connectTimeout, int readTimeout) throws Exception {
        String urlStr = protocol + "://" + host + ":" + porta + "/api/login";
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            Map<String, String> credenciais = new HashMap<>();
            credenciais.put("usuario", usuario);
            credenciais.put("senha", senha);
            String body = gson.toJson(credenciais);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                String response = lerResposta(conn);
                JsonObject json = gson.fromJson(response, JsonObject.class);
                return json.get("token").getAsString();
            } else {
                throw new Exception("Falha na autenticação: HTTP " + responseCode);
            }

        } finally {
            conn.disconnect();
        }
    }

    public String consultarIQA(long inicio, long fim) throws Exception {
        String endpoint = "/relatorios/iqa?inicio=" + inicio + "&fim=" + fim;
        return fazerRequisicaoGET(endpoint);
    }

    public String consultarTendencias(long inicio, long fim) throws Exception {
        String endpoint = "/relatorios/tendencias?inicio=" + inicio + "&fim=" + fim;
        return fazerRequisicaoGET(endpoint);
    }

    public String consultarMicroclima(String localizacao, long inicio, long fim) throws Exception {
        String endpoint = "/relatorios/microclima?localizacao=" + localizacao + 
                         "&inicio=" + inicio + "&fim=" + fim;
        return fazerRequisicaoGET(endpoint);
    }

    public String consultarEnchentes(String localizacao) throws Exception {
        String endpoint = "/relatorios/enchentes?localizacao=" + localizacao;
        return fazerRequisicaoGET(endpoint);
    }

    public String consultarTrafego(String localizacao, long inicio, long fim) throws Exception {
        String endpoint = "/relatorios/trafego?localizacao=" + localizacao + 
                         "&inicio=" + inicio + "&fim=" + fim;
        return fazerRequisicaoGET(endpoint);
    }

    public String consultarStatus() throws Exception {
        return fazerRequisicaoGET("/status");
    }

    private String fazerRequisicaoGET(String endpoint) throws Exception {
        String urlStr = urlBase + endpoint;
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                return lerResposta(conn);
            } else if (responseCode == 401) {
                throw new Exception("Token inválido ou expirado");
            } else if (responseCode == 404) {
                throw new Exception("Endpoint não encontrado ou sem dados");
            } else {
                String erro = lerErro(conn);
                throw new Exception("Erro HTTP " + responseCode + ": " + erro);
            }
            
        } finally {
            conn.disconnect();
        }
    }

    private static String lerResposta(HttpURLConnection conn) throws Exception {
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                response.append(linha);
            }
        }
        return response.toString();
    }

    private static String lerErro(HttpURLConnection conn) {
        try {
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = br.readLine()) != null) {
                    response.append(linha);
                }
            }
            return response.toString();
        } catch (Exception e) {
            return "Erro desconhecido";
        }
    }

    public static String extrairCampo(String json, String campo) {
        try {
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            if (jsonObject.has(campo)) {
                return jsonObject.get(campo).getAsString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void inspecionarToken(String token) {
        if (token == null || token.isEmpty()) {
            logger.warn("Token vazio");
            return;
        }
        
        try {
            // JWT tem 3 partes: HEADER.PAYLOAD.SIGNATURE
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                logger.warn("Token JWT inválido (formato incorreto)");
                return;
            }
            
            // Decodificar HEADER (Base64URL)
            byte[] headerBytes = java.util.Base64.getUrlDecoder().decode(parts[0]);
            String header = new String(headerBytes, StandardCharsets.UTF_8);
            
            // Decodificar PAYLOAD (Base64URL)
            byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║              INSPEÇÃO DO TOKEN JWT                        ║");
            System.out.println("╠═══════════════════════════════════════════════════════════╣");
            System.out.println("║  HEADER (algoritmo e tipo):                               ║");
            System.out.println("║  " + header);
            System.out.println("║                                                           ║");
            System.out.println("║  PAYLOAD (claims):                                        ║");
            System.out.println("║  " + payload);
            System.out.println("║                                                           ║");
            System.out.println("║  SIGNATURE (assinatura HMAC-SHA256):                      ║");
            System.out.println("║  " + parts[2].substring(0, Math.min(50, parts[2].length())) + "...");
            System.out.println("╚═══════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            logger.error("Erro ao inspecionar token: {}", e.getMessage(), e);
        }
    }
}
