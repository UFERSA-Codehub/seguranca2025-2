package com.project.client;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.URI;
import java.net.SocketException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
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

public class Client implements IClient {
    private static final Logger logger = LoggerFactory.getLogger("Client");
    private static final int TIMEOUT_MS = 5000;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATACENTER_ID = "DATACENTER";

    private final String clientId;
    private final String username;
    private final String password;
    private final String discoveryHost;
    private final int discoveryPort;

    private KeyManager keyManager;
    private SecureUDPChannel channel;
    private HttpClient httpClient;
    private String datacenterUrl;
    private String jwtToken;

    public Client(String clientId, String username, String password, String discoveryHost, int discoveryPort) {
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
    }

    @Override
    public void start() {
        logger.info("[Cliente {}] Iniciando...", clientId);

        try {
            this.keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            this.channel = new SecureUDPChannel(clientId, keyManager, socket);
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (NoSuchAlgorithmException e) {
            logger.error("[Cliente {}] Erro ao inicializar KeyManager: {}", clientId, e.getMessage());
            return;
        } catch (SocketException e) {
            logger.error("[Cliente {}] Erro ao abrir socket: {}", clientId, e.getMessage());
            return;
        }

        try {
            // Passo 1 - Descobrir Datacenter (UDP)
            if (!discoverDatacenter()) {
                logger.error("Falha ao descobrir Datacenter");
                return;
            }

            // Passo 2 - Handshake com Datacenter (HTTP)
            if (!handshakeWithDatacenter()) {
                logger.error("Falha no handshake com Datacenter");
                return;
            }

            // Passo 3 - Autenticar (HTTP cifrado)
            if (!authenticate()) {
                logger.error("Falha na autenticação");
                return;
            }

            // Passo 4 - Executar consultas (HTTP cifrado)
            runQueries();

        } finally {
            stop();
        }
    }

    private boolean discoverDatacenter() {
        // Handshake com Discovery
        logger.info("[Cliente {}] Handshake com Discovery...", clientId);
        channel.send(channel.buildHello(), discoveryHost, discoveryPort);

        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.error("Timeout aguardando resposta do Discovery");
            return false;
        }

        MessageUDP challenge = packet.message();
        if (challenge == null || challenge.getType() != MessageTypeUDP.CHALLENGE) {
            logger.error("Resposta inválida do Discovery");
            return false;
        }

        if (!channel.handleChallenge(challenge)) {
            logger.error("Falha ao processar CHALLENGE");
            return false;
        }

        // Requisitar localização do Datacenter (protocolo HTTP)
        logger.info("[Cliente {}] Buscando Datacenter...", clientId);
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
            logger.error("Timeout aguardando localização do Datacenter");
            return false;
        }

        MessageUDP response = packet.message();
        if (response == null) {
            logger.error("Resposta nula do Discovery");
            return false;
        }

        // Verificar e decifrar envelope
        if (!channel.verify(response)) {
            logger.error("Falha na verificação da resposta do Discovery");
            return false;
        }

        var envelope = channel.decryptEnvelope("DISCOVERY", response);
        if (envelope == null) {
            logger.error("Falha ao decifrar envelope do Discovery");
            return false;
        }

        if (envelope.getType() == MessageTypeUDP.NOT_FOUND) {
            logger.error("Nenhum Datacenter disponível");
            return false;
        }

        String[] parts = envelope.getPayload().split(":");
        this.datacenterUrl = "http://" + parts[0] + ":" + parts[1];
        logger.info("[Cliente {}] Datacenter encontrado: {}", clientId, datacenterUrl);
        return true;
    }

    private boolean handshakeWithDatacenter() {
        logger.info("[Cliente {}] Handshake com Datacenter...", clientId);

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

            MessageHTTP challenge = MessageHTTP.fromJson(response.body());
            if (!handleChallenge(challenge)) {
                return false;
            }

            logger.info("[Cliente {}] Handshake com Datacenter completo", clientId);
            return true;

        } catch (IOException | InterruptedException e) {
            logger.error("Erro no handshake: {}", e.getMessage());
            return false;
        }
    }

    private boolean authenticate() {
        logger.info("[Cliente {}] Autenticando com Datacenter...", clientId);

        try {
            JsonObject credentials = new JsonObject();
            credentials.addProperty("username", username);
            credentials.addProperty("password", password);

            MessageHTTP authRequest = buildEncrypted(MessageTypeHTTP.AUTH, credentials.toString());
            if (authRequest == null) {
                logger.error("Falha ao construir mensagem de autenticação");
                return false;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(datacenterUrl + "/auth"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(authRequest.toJson()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("[Cliente {}] Autenticação falhou: {} - {}", clientId, response.statusCode(), response.body());
                return false;
            }

            MessageHTTP authResponse = MessageHTTP.fromJson(response.body());
            String payload = decrypt(authResponse);
            if (payload == null) {
                logger.error("Falha ao decifrar resposta de autenticação");
                return false;
            }

            JsonObject body = gson.fromJson(payload, JsonObject.class);
            this.jwtToken = body.get("token").getAsString();

            logger.info("[Cliente {}] Autenticado com sucesso", clientId);
            return true;

        } catch (IOException | InterruptedException e) {
            logger.error("[Cliente {}] Erro na autenticação: {}", clientId, e.getMessage());
            return false;
        }
    }

    private void runQueries() {
        logger.info("");
        logger.info("=== EXECUTANDO CONSULTAS ===");
        logger.info("");

        queryReport("pollution", "Relatório de Poluição");
        queryReport("flood", "Alerta de Enchente");
        queryReport("noise", "Mapa de Ruído");
        queryReport("uv", "Índice UV");
        queryReport("air-quality", "Qualidade do Ar");
    }

    private void queryReport(String reportType, String title) {
        logger.info("--- {} ---", title);

        try {
            JsonObject queryPayload = new JsonObject();
            queryPayload.addProperty("type", reportType);

            MessageHTTP queryRequest = buildEncrypted(MessageTypeHTTP.QUERY_REPORT, queryPayload.toString());
            if (queryRequest == null) {
                logger.error("Falha ao construir requisição");
                return;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(datacenterUrl + "/report"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(queryRequest.toJson()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Erro ao consultar relatório: {}", response.body());
                return;
            }

            MessageHTTP queryResponse = MessageHTTP.fromJson(response.body());
            String reportData = decrypt(queryResponse);

            if (reportData != null) {
                JsonObject report = gson.fromJson(reportData, JsonObject.class);
                logger.info("Tipo: {}", report.get("type").getAsString());
                logger.info("Conteúdo recebido ({} caracteres)", report.get("content").getAsString().length());
            }

        } catch (IOException | InterruptedException e) {
            logger.error("Erro na consulta: {}", e.getMessage());
        }
        
        logger.info("");
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
                logger.error("Assinatura inválida na resposta do Datacenter");
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

    @Override
    public void stop() {
        logger.info("[Cliente {}] Encerrando...", clientId);
        if (channel != null) {
            channel.getSocket().close();
        }
    }

    @Override
    public boolean isRunning() {
        return channel != null && !channel.getSocket().isClosed();
    }

    @Override
    public String getName() {
        return clientId;
    }

    public static void main(String[] args) {
        String clientId = args.length > 0 ? args[0] : "CLIENT_" + UUID.randomUUID().toString().substring(0, 8);
        String username = args.length > 1 ? args[1] : "admin";
        String password = args.length > 2 ? args[2] : "admin123";
        String discoveryHost = args.length > 3 ? args[3] : "localhost";
        int discoveryPort = args.length > 4 ? Integer.parseInt(args[4]) : 4000;

        Client client = new Client(clientId, username, password, discoveryHost, discoveryPort);
        client.start();
    }
}
