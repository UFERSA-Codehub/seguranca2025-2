package com.project.client;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Scanner;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import com.project.crypto.AES;
import com.project.crypto.KeyManager;
import com.project.message.http.MessageHTTP;
import com.project.message.http.MessageTypeHTTP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;

public class ClientApp {
    private static final Logger logger = LoggerFactory.getLogger("Client.App");
    private static final int TIMEOUT_MS = 5000;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATACENTER_ID = "DATACENTER";

    private final String clientId;
    private final String discoveryHost;
    private final int discoveryPort;
    private final Scanner scanner;

    private KeyManager keyManager;
    private SecureUDPChannel channel;
    private HttpClient httpClient;
    private String datacenterUrl;
    private String jwtToken;
    private String loggedUser;
    private boolean running;

    public ClientApp(String clientId, String discoveryHost, int discoveryPort) {
        this.clientId = clientId;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
        this.scanner = new Scanner(System.in);
        this.running = true;
    }

    public void start() {
        printHeader();

        // Passo 1 - Inicializar componentes de rede e crypto
        if (!initialize()) {
            return;
        }

        // Passo 2 - Descobrir e conectar ao Datacenter
        if (!connectToDatacenter()) {
            return;
        }

        // Passo 3 - Loop do menu principal
        mainLoop();

        // Passo 4 - Encerrar
        stop();
    }

    private boolean initialize() {
        try {
            this.keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            this.channel = new SecureUDPChannel(clientId, keyManager, socket);
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            return true;
        } catch (NoSuchAlgorithmException e) {
            System.out.println("[ERRO] Falha ao inicializar criptografia: " + e.getMessage());
            return false;
        } catch (SocketException e) {
            System.out.println("[ERRO] Falha ao abrir socket: " + e.getMessage());
            return false;
        }
    }

    private boolean connectToDatacenter() {
        System.out.println("\n[*] Conectando ao sistema...");

        // Descobrir Datacenter via Discovery (UDP)
        if (!discoverDatacenter()) {
            System.out.println("[ERRO] Nao foi possivel localizar o Datacenter");
            return false;
        }

        // Handshake com Datacenter (HTTP)
        if (!handshakeWithDatacenter()) {
            System.out.println("[ERRO] Falha no handshake com Datacenter");
            return false;
        }

        System.out.println("[OK] Conectado ao Datacenter: " + datacenterUrl);
        return true;
    }

    private void mainLoop() {
        while (running) {
            clearScreen();
            printHeader();
            printMenu();
            String choice = prompt("Opcao");

            switch (choice) {
                case "1" -> doLogin();
                case "2" -> doQueryReport();
                case "3" -> doLogout();
                case "0" -> running = false;
                default -> System.out.println("[!] Opcao invalida");
            }
        }
    }

    // ==================== MENU ACTIONS ====================

    private void doLogin() {
        if (jwtToken != null) {
            System.out.println("[!] Voce ja esta logado como: " + loggedUser);
            waitEnter();
            return;
        }

        clearScreen();
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│                  LOGIN                   │");
        System.out.println("└──────────────────────────────────────────┘");
        System.out.println();
        String username = prompt("Usuario");
        String password = prompt("Senha");

        System.out.println("\n[*] Autenticando...");
        if (authenticate(username, password)) {
            this.loggedUser = username;
            System.out.println("[OK] Login bem-sucedido!");
        } else {
            System.out.println("[ERRO] Credenciais invalidas ou erro de conexao");
        }
        waitEnter();
    }

    private void doLogout() {
        if (jwtToken == null) {
            System.out.println("[!] Voce nao esta logado");
            waitEnter();
            return;
        }

        this.jwtToken = null;
        this.loggedUser = null;
        System.out.println("[OK] Logout realizado");
        waitEnter();
    }

    private void doQueryReport() {
        if (!checkAuth()) return;

        clearScreen();
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│          CONSULTAR RELATORIO             │");
        System.out.println("├──────────────────────────────────────────┤");
        System.out.println("│  1. Relatorio de Poluicao                │");
        System.out.println("│  2. Alerta de Enchente                   │");
        System.out.println("│  3. Mapa de Ruido                        │");
        System.out.println("│  4. Indice UV                            │");
        System.out.println("│  5. Qualidade do Ar                      │");
        System.out.println("│  0. Voltar                               │");
        System.out.println("└──────────────────────────────────────────┘");

        String choice = prompt("Opcao");
        String reportType = switch (choice) {
            case "1" -> "pollution";
            case "2" -> "flood";
            case "3" -> "noise";
            case "4" -> "uv";
            case "5" -> "air-quality";
            case "0" -> null;
            default -> null;
        };

        if (reportType == null) {
            return;
        }

        queryReport(reportType);
    }

    private boolean checkAuth() {
        if (jwtToken == null) {
            System.out.println("[!] Voce precisa fazer login primeiro (opcao 1)");
            return false;
        }
        return true;
    }

    // ==================== DISCOVERY (UDP) ====================

    private boolean discoverDatacenter() {
        // Handshake com Discovery
        channel.send(channel.buildHello(), discoveryHost, discoveryPort);

        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("Timeout aguardando resposta do Discovery");
            return false;
        }

        MessageUDP challenge = packet.message();
        if (challenge == null || challenge.getType() != MessageTypeUDP.CHALLENGE) {
            logger.error("Resposta invalida do Discovery");
            return false;
        }

        if (!channel.handleChallenge(challenge)) {
            logger.error("Falha ao processar CHALLENGE");
            return false;
        }

        // Requisitar localizacao do Datacenter
        JsonObject lookPayload = new JsonObject();
        lookPayload.addProperty("protocol", "http");
        MessageUDP lookDc = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.LOOK_DATACENTER, lookPayload.toString());
        if (lookDc == null) {
            logger.error("Falha ao construir mensagem LOOK_DATACENTER");
            return false;
        }
        channel.send(lookDc, discoveryHost, discoveryPort);

        packet = channel.receive();
        if (packet == null) {
            logger.error("Timeout aguardando localizacao do Datacenter");
            return false;
        }

        MessageUDP response = packet.message();
        if (response == null) {
            logger.error("Resposta nula do Discovery");
            return false;
        }

        // Verificar e decifrar envelope
        if (!channel.verify(response)) {
            logger.error("Falha na verificacao da resposta do Discovery");
            return false;
        }

        var envelope = channel.decryptEnvelope("DISCOVERY", response);
        if (envelope == null) {
            logger.error("Falha ao decifrar envelope do Discovery");
            return false;
        }

        if (envelope.getType() == MessageTypeUDP.NOT_FOUND) {
            logger.error("Nenhum Datacenter disponivel");
            return false;
        }

        String[] parts = envelope.getPayload().split(":");
        this.datacenterUrl = "http://" + parts[0] + ":" + parts[1];
        return true;
    }

    // ==================== HTTP API ====================

    private boolean handshakeWithDatacenter() {
        try {
            MessageHTTP hello = buildHello();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(datacenterUrl + "/handshake"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(hello.toJson()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Handshake falhou: {}", response.body());
                return false;
            }

            MessageHTTP challengeMsg = MessageHTTP.fromJson(response.body());
            return handleChallenge(challengeMsg);

        } catch (IOException | InterruptedException e) {
            logger.error("Erro no handshake: {}", e.getMessage());
            return false;
        }
    }

    private boolean authenticate(String username, String password) {
        try {
            JsonObject credentials = new JsonObject();
            credentials.addProperty("username", username);
            credentials.addProperty("password", password);

            MessageHTTP authRequest = buildEncrypted(MessageTypeHTTP.AUTH, credentials.toString());
            if (authRequest == null) {
                return false;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(datacenterUrl + "/auth"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(authRequest.toJson()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Autenticacao falhou: {}", response.body());
                return false;
            }

            MessageHTTP authResponse = MessageHTTP.fromJson(response.body());
            String payload = decrypt(authResponse);
            if (payload == null) {
                return false;
            }

            JsonObject body = gson.fromJson(payload, JsonObject.class);
            this.jwtToken = body.get("token").getAsString();
            return true;

        } catch (IOException | InterruptedException e) {
            logger.error("Erro na autenticacao: {}", e.getMessage());
            return false;
        }
    }

    private void queryReport(String reportType) {
        clearScreen();
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│  " + padRight(getReportTitle(reportType), 39) + " │");
        System.out.println("└──────────────────────────────────────────┘");
        System.out.println();
        System.out.println("[*] Consultando...");

        try {
            JsonObject queryPayload = new JsonObject();
            queryPayload.addProperty("type", reportType);
            queryPayload.addProperty("format", "json");

            MessageHTTP queryRequest = buildEncrypted(MessageTypeHTTP.QUERY_REPORT, queryPayload.toString());
            if (queryRequest == null) {
                System.out.println("[ERRO] Falha ao construir requisicao");
                waitEnter();
                return;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(datacenterUrl + "/report"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(queryRequest.toJson()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("[ERRO] Falha ao obter relatorio: " + response.body());
                waitEnter();
                return;
            }

            MessageHTTP queryResponse = MessageHTTP.fromJson(response.body());
            String reportData = decrypt(queryResponse);

            if (reportData != null) {
                displayReport(reportType, reportData);
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("[ERRO] Falha na consulta: " + e.getMessage());
        }
        waitEnter();
    }

    private void displayReport(String reportType, String reportData) {
        JsonObject response = gson.fromJson(reportData, JsonObject.class);
        
        // Verificar se temos dados JSON (novo formato)
        if (!response.has("data")) {
            System.out.println("[ERRO] Formato de resposta invalido");
            return;
        }

        JsonObject data = response.getAsJsonObject("data");
        
        // Info basica
        int totalReadings = data.has("totalReadings") ? data.get("totalReadings").getAsInt() : 0;
        long totalSensors = data.has("totalSensors") ? data.get("totalSensors").getAsLong() : 0;
        
        if (totalReadings == 0) {
            System.out.println("Nenhum dado disponivel para o relatorio solicitado.");
            return;
        }
        
        System.out.println();
        System.out.println("Leituras: " + totalReadings + " | Sensores: " + totalSensors);
        System.out.println("────────────────────────────────────────────");

        switch (reportType) {
            case "flood" -> {
                String alertLevel = data.has("alertLevel") ? data.get("alertLevel").getAsString() : "N/A";
                String avgHumidity = data.has("avgHumidity") ? data.get("avgHumidity").getAsString() : "N/A";
                String avgTemp = data.has("avgTemperature") ? data.get("avgTemperature").getAsString() : "N/A";
                
                System.out.println("Status: " + alertLevel);
                System.out.println("Umidade Media: " + avgHumidity + "%");
                System.out.println("Temperatura Media: " + avgTemp + " C");
                
                if ("CRITICO".equals(alertLevel)) {
                    System.out.println("\n[!] ALERTA DE ENCHENTE DETECTADO");
                } else if ("ATENCAO".equals(alertLevel)) {
                    System.out.println("\n[!] ATENCAO: Condicoes de risco");
                }
            }
            case "pollution" -> {
                String co2Status = data.has("co2Status") ? data.get("co2Status").getAsString() : "N/A";
                String avgCo2 = data.has("avgCo2") ? data.get("avgCo2").getAsString() : "N/A";
                String avgNo2 = data.has("avgNo2") ? data.get("avgNo2").getAsString() : "N/A";
                String avgPm25 = data.has("avgPm25") ? data.get("avgPm25").getAsString() : "N/A";
                
                System.out.println("Status CO2: " + co2Status);
                System.out.println("CO2 Medio: " + avgCo2 + " ppm");
                System.out.println("NO2 Medio: " + avgNo2 + " ug/m3");
                System.out.println("PM2.5 Medio: " + avgPm25 + " ug/m3");
            }
            case "noise" -> {
                String noiseLevel = data.has("noiseLevel") ? data.get("noiseLevel").getAsString() : "N/A";
                String avgNoise = data.has("avgNoise") ? data.get("avgNoise").getAsString() : "N/A";
                
                System.out.println("Nivel de Ruido: " + noiseLevel);
                System.out.println("Media: " + avgNoise + " dB");
            }
            case "uv" -> {
                String uvLevel = data.has("uvLevel") ? data.get("uvLevel").getAsString() : "N/A";
                String avgUv = data.has("avgUv") ? data.get("avgUv").getAsString() : "N/A";
                
                System.out.println("Nivel UV: " + uvLevel);
                System.out.println("Indice Medio: " + avgUv);
            }
            case "air-quality" -> {
                String iqaValue = data.has("iqaValue") ? String.valueOf(data.get("iqaValue").getAsInt()) : "N/A";
                String classification = data.has("iqaClassification") ? data.get("iqaClassification").getAsString() : "N/A";
                String mainPollutant = data.has("mainPollutant") ? data.get("mainPollutant").getAsString() : "N/A";
                
                System.out.println("IQA: " + iqaValue + " (" + classification + ")");
                System.out.println("Principal Poluente: " + mainPollutant);
            }
        }

        // Recomendacao
        if (data.has("recommendation")) {
            String recommendation = data.get("recommendation").getAsString();
            System.out.println("\nRecomendacao:");
            System.out.println("  " + recommendation);
        }

        System.out.println();
    }

    private String extractFromHtml(String html, String field) {
        // Tentar extrair de um span com id ou class correspondente
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?:id|class)=[\"']?" + field + "[\"']?[^>]*>([^<]+)<",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Tentar extrair de data attribute
        pattern = java.util.regex.Pattern.compile(
            "data-" + field + "=[\"']([^\"']+)[\"']",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }

    // ==================== CRYPTO HELPERS ====================

    private MessageHTTP buildHello() {
        return MessageHTTP.builder()
                .type(MessageTypeHTTP.HELLO)
                .clientId(clientId)
                .senderPublicKey(keyManager.getPublicKeyBase64())
                .build();
    }

    private boolean handleChallenge(MessageHTTP challenge) {
        try {
            keyManager.storePeerKey(DATACENTER_ID, challenge.getSenderPublicKey());
            keyManager.decryptAndStoreSessionKeys(DATACENTER_ID, challenge.getEncryptedSessionKeys());
            return true;
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao processar CHALLENGE: {}", e.getMessage());
            return false;
        }
    }

    private MessageHTTP buildEncrypted(MessageTypeHTTP type, String payload) {
        try {
            SecretKey aesKey = keyManager.getPeerAESKey(DATACENTER_ID);
            AES aes = new AES(aesKey);

            String encryptedPayload = aes.encrypt(payload);
            // Assinar ciphertext diretamente (sem HMAC)
            String signature = keyManager.signBase64(encryptedPayload.getBytes());

            MessageHTTP.Builder builder = MessageHTTP.builder()
                    .type(type)
                    .clientId(clientId)
                    .encryptedPayload(encryptedPayload)
                    .signature(signature);

            if (jwtToken != null) {
                builder.jwtToken(jwtToken);
            }

            return builder.build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao construir mensagem cifrada: {}", e.getMessage());
            return null;
        }
    }

    private String decrypt(MessageHTTP response) {
        try {
            // Verificar assinatura primeiro (encrypt-then-MAC)
            String signature = response.getSignature();
            String encryptedPayload = response.getEncryptedPayload();

            if (signature == null || encryptedPayload == null) {
                logger.error("Resposta incompleta do Datacenter (assinatura ou payload ausente)");
                return null;
            }

            byte[] ciphertext = encryptedPayload.getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes = Base64.getDecoder().decode(signature);

            if (!keyManager.verifySignature(DATACENTER_ID, ciphertext, signatureBytes)) {
                logger.error("Assinatura invalida na resposta do Datacenter");
                return null;
            }

            // Decifrar payload
            AES aes = new AES(keyManager.getPeerAESKey(DATACENTER_ID));
            return aes.decrypt(encryptedPayload);
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao decifrar resposta: {}", e.getMessage());
            return null;
        }
    }

    // ==================== UI HELPERS ====================

    private void printHeader() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     CLIENTE INTERATIVO - DATACENTER      ║");
        System.out.println("║          Comunicacao Cifrada             ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println("  ID: " + clientId);
    }

    private void printMenu() {
        System.out.println();
        System.out.println("┌──────────────────────────────────────────┐");
        if (jwtToken != null) {
            System.out.println("│  Logado como: " + padRight(loggedUser, 26) + " │");
        } else {
            System.out.println("│  Status: Nao autenticado                 │");
        }
        System.out.println("├──────────────────────────────────────────┤");
        System.out.println("│  1. Login                                │");
        System.out.println("│  2. Consultar Relatorio                  │");
        System.out.println("│  3. Logout                               │");
        System.out.println("│  0. Sair                                 │");
        System.out.println("└──────────────────────────────────────────┘");
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine().trim();
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private void waitEnter() {
        prompt("Pressione ENTER para continuar");
    }

    private String getReportTitle(String type) {
        return switch (type) {
            case "pollution" -> "RELATORIO DE POLUICAO";
            case "flood" -> "ALERTA DE ENCHENTE";
            case "noise" -> "MAPA DE RUIDO";
            case "uv" -> "INDICE UV";
            case "air-quality" -> "QUALIDADE DO AR";
            default -> type.toUpperCase();
        };
    }

    private void stop() {
        System.out.println("\n[*] Encerrando cliente...");
        if (channel != null) {
            channel.getSocket().close();
        }
        scanner.close();
        System.out.println("[OK] Ate logo!");
    }

    public static void main(String[] args) {
        String clientId = args.length > 0 ? args[0] : "CLIENT_" + UUID.randomUUID().toString().substring(0, 8);
        String discoveryHost = args.length > 1 ? args[1] : "localhost";
        int discoveryPort = args.length > 2 ? Integer.parseInt(args[2]) : 4000;

        ClientApp app = new ClientApp(clientId, discoveryHost, discoveryPort);
        app.start();
    }
}
