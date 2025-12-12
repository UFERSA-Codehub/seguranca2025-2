package com.project.server.datacenter;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.message.tcp.EnvelopeTCP;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;

public class AuthClient {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter.AuthClient");
    private static final Gson gson = new Gson();
    
    private static final String DEFAULT_AUTH_HOST = "localhost";
    private static final int DEFAULT_AUTH_PORT = 4001;

    private final String datacenterId;
    private String authHost;
    private int authPort;
    private SecureTCPChannel channel;
    private volatile boolean connected;

    public AuthClient(String datacenterId) {
        this.datacenterId = datacenterId;
        this.authHost = DEFAULT_AUTH_HOST;
        this.authPort = DEFAULT_AUTH_PORT;
        this.connected = false;
    }

    public boolean hasAuthServer() {
        return authHost != null && authPort > 0;
    }

    public boolean connect() {
        if (!hasAuthServer()) {
            logger.warn("AuthServer não configurado");
            return false;
        }

        if (connected && channel != null) {
            return true;
        }

        String peerInfo = authHost + ":" + authPort;
        logger.info("Conectando ao AuthServer ({})...", peerInfo);

        try {
            Socket socket = new Socket(authHost, authPort);
            socket.setSoTimeout(10_000);
            KeyManager keyManager = new KeyManager();
            this.channel = new SecureTCPChannel(datacenterId, keyManager, socket);
            channel.setTracePeerId("AUTH");

            // Passo 1 - Enviar HELLO
            channel.send(channel.buildHello());
            logger.debug("HELLO enviado para AUTH ({})", peerInfo);

            // Passo 2 - Receber CHALLENGE
            MessageTCP challenge = channel.receive();
            if (challenge == null || challenge.getType() != MessageTypeTCP.CHALLENGE) {
                logger.error("Resposta inesperada do AuthServer: {}", challenge != null ? challenge.getType() : "null");
                return false;
            }
            logger.debug("CHALLENGE recebido de AUTH ({})", peerInfo);

            if (!channel.handleChallenge(challenge)) {
                logger.error("Falha ao processar CHALLENGE do AuthServer");
                return false;
            }

            this.connected = true;
            logger.info("Conectado ao AuthServer ({})", peerInfo);
            return true;

        } catch (IOException e) {
            logger.error("Erro ao conectar ao AuthServer ({}): {}", peerInfo, e.getMessage());
            return false;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao criar KeyManager: {}", e.getMessage());
            return false;
        }
    }

    public boolean ensureConnected() {
        if (connected && channel != null) {
            return true;
        }
        return connect();
    }

    public String authenticate(String username, String password) {
        if (!ensureConnected()) {
            logger.error("Não foi possível conectar ao AuthServer");
            return null;
        }

        try {
            // Construir payload VALIDATE
            JsonObject payload = new JsonObject();
            payload.addProperty("username", username);
            payload.addProperty("password", password);

            // Enviar VALIDATE com envelope cifrado
            MessageTCP validateMsg = channel.buildEncryptedEnvelope("AUTH", MessageTypeTCP.VALIDATE, payload.toString());
            if (validateMsg == null) {
                logger.error("Falha ao construir mensagem VALIDATE");
                return null;
            }
            channel.send(validateMsg);
            logger.debug("VALIDATE enviado para AUTH - username: {}", username);

            // Receber resposta (envelope cifrado)
            MessageTCP response = channel.receive();
            if (response == null) {
                logger.error("Timeout aguardando resposta do AuthServer");
                markDisconnected();
                return null;
            }

            // Verificar e decifrar envelope
            if (!channel.verify(response)) {
                logger.error("Falha ao verificar resposta do AuthServer");
                return null;
            }

            EnvelopeTCP envelope = channel.decryptEnvelope("AUTH", response);
            if (envelope == null) {
                logger.error("Falha ao decifrar envelope do AuthServer");
                return null;
            }

            if (envelope.getType() == MessageTypeTCP.VALIDATE_FAIL) {
                logger.warn("Autenticação rejeitada para {}: {}", username, envelope.getPayload());
                return null;
            }

            if (envelope.getType() != MessageTypeTCP.VALIDATE_OK) {
                logger.error("Resposta inesperada do AuthServer: {}", envelope.getType());
                return null;
            }

            // Extrair token do payload
            JsonObject responsePayload = gson.fromJson(envelope.getPayload(), JsonObject.class);
            String token = responsePayload.get("token").getAsString();
            logger.info("Token obtido do AuthServer para {}", username);
            return token;

        } catch (Exception e) {
            logger.error("Erro na autenticação via AuthServer: {}", e.getMessage());
            return null;
        }
    }

    private void markDisconnected() {
        connected = false;
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    public void disconnect() {
        markDisconnected();
        logger.info("Desconectado do AuthServer");
    }

    public boolean isConnected() {
        return connected;
    }
}
