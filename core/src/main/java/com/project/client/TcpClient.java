package com.project.client;

import java.io.IOException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.message.tcp.EnvelopeTCP;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;

/**
 * Cliente TCP para comunicação segura com AuthServer e Datacenter.
 * Usado pelo ClientApp (CLI interativo).
 */
public class TcpClient {
    private static final Logger logger = LoggerFactory.getLogger("Client.TcpClient");
    private static final Gson gson = new Gson();
    private static final int SOCKET_TIMEOUT_MS = 10_000;

    private final String clientId;
    private final KeyManager keyManager;

    private SecureTCPChannel authChannel;
    private SecureTCPChannel datacenterChannel;
    private String jwtToken;

    public TcpClient(String clientId, KeyManager keyManager) {
        this.clientId = clientId;
        this.keyManager = keyManager;
    }

    // ==================== AUTENTICAÇÃO ====================

    /**
     * Autentica com o AuthServer via TCP.
     * Envia VALIDATE com username/password, recebe VALIDATE_OK com JWT.
     */
    public String authenticateWithAuthServer(String authHost, int authPort, String username, String password) {
        logger.info("[Client {}] Conectando ao AuthServer ({}:{})...", clientId, authHost, authPort);

        try {
            Socket socket = new Socket(authHost, authPort);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            this.authChannel = new SecureTCPChannel(clientId, keyManager, socket);
            // Trace mostra PACKET_FILTER como peer (firewall intercepta)
            authChannel.setTracePeerId("PACKET_FILTER");

            if (!performHandshake(authChannel, "AUTH")) {
                logger.error("[Client {}] Falha no handshake com AuthServer", clientId);
                closeAuthChannel();
                return null;
            }

            // Enviar VALIDATE com credenciais
            JsonObject authPayload = new JsonObject();
            authPayload.addProperty("username", username);
            authPayload.addProperty("password", password);

            MessageTCP validateMsg = authChannel.buildEncryptedEnvelope("AUTH", MessageTypeTCP.VALIDATE, authPayload.toString());
            if (validateMsg == null) {
                logger.error("[Client {}] Falha ao construir mensagem VALIDATE", clientId);
                closeAuthChannel();
                return null;
            }
            authChannel.send(validateMsg);

            // Receber resposta
            MessageTCP response = authChannel.receive();
            if (response == null) {
                logger.error("[Client {}] Timeout aguardando resposta de autenticação", clientId);
                closeAuthChannel();
                return null;
            }

            if (!authChannel.verify(response)) {
                logger.error("[Client {}] Falha ao verificar resposta de autenticação", clientId);
                closeAuthChannel();
                return null;
            }

            EnvelopeTCP envelope = authChannel.decryptEnvelope("AUTH", response);
            if (envelope == null) {
                logger.error("[Client {}] Falha ao decifrar envelope de autenticação", clientId);
                closeAuthChannel();
                return null;
            }

            if (envelope.getType() == MessageTypeTCP.VALIDATE_FAIL) {
                logger.warn("[Client {}] Autenticação falhou: {}", clientId, envelope.getPayload());
                closeAuthChannel();
                return null;
            }

            if (envelope.getType() != MessageTypeTCP.VALIDATE_OK) {
                logger.error("[Client {}] Resposta inesperada: {}", clientId, envelope.getType());
                closeAuthChannel();
                return null;
            }

            // Extrair token
            JsonObject responsePayload = gson.fromJson(envelope.getPayload(), JsonObject.class);
            this.jwtToken = responsePayload.get("token").getAsString();
            logger.info("[Client {}] Autenticado com sucesso no AuthServer", clientId);

            closeAuthChannel();
            return jwtToken;

        } catch (IOException e) {
            logger.error("[Client {}] Erro de conexão com AuthServer: {}", clientId, e.getMessage());
            return null;
        }
    }

    // ==================== DATACENTER ====================

    /**
     * Conecta ao Datacenter via TCP (handshake).
     */
    public boolean connectToDatacenter(String datacenterHost, int datacenterPort) {
        logger.info("[Client {}] Conectando ao Datacenter ({}:{})...", clientId, datacenterHost, datacenterPort);

        try {
            Socket socket = new Socket(datacenterHost, datacenterPort);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            this.datacenterChannel = new SecureTCPChannel(clientId, keyManager, socket);
            // Trace mostra PACKET_FILTER como peer (firewall intercepta)
            datacenterChannel.setTracePeerId("PACKET_FILTER");

            if (!performHandshake(datacenterChannel, "DATACENTER")) {
                logger.error("[Client {}] Falha no handshake com Datacenter", clientId);
                closeDatacenterChannel();
                return false;
            }

            logger.info("[Client {}] Conectado ao Datacenter", clientId);
            return true;

        } catch (IOException e) {
            logger.error("[Client {}] Erro de conexão com Datacenter: {}", clientId, e.getMessage());
            return false;
        }
    }

    /**
     * Consulta um relatório no Datacenter.
     * Envia QUERY_REPORT com tipo e formato, recebe QUERY_RESPONSE com dados.
     */
    public String queryReport(String reportType, String format) {
        if (datacenterChannel == null) {
            logger.error("[Client {}] Não conectado ao Datacenter", clientId);
            return null;
        }

        if (jwtToken == null) {
            logger.error("[Client {}] Não autenticado - token ausente", clientId);
            return null;
        }

        try {
            // Construir payload da consulta
            JsonObject queryPayload = new JsonObject();
            queryPayload.addProperty("type", reportType);
            queryPayload.addProperty("format", format);

            // Enviar QUERY_REPORT com JWT
            MessageTCP queryMsg = datacenterChannel.buildEncryptedEnvelope(
                "DATACENTER", MessageTypeTCP.QUERY_REPORT, queryPayload.toString(), jwtToken
            );
            if (queryMsg == null) {
                logger.error("[Client {}] Falha ao construir mensagem QUERY_REPORT", clientId);
                return null;
            }
            datacenterChannel.send(queryMsg);

            // Receber resposta
            MessageTCP response = datacenterChannel.receive();
            if (response == null) {
                logger.error("[Client {}] Timeout aguardando resposta do Datacenter", clientId);
                return null;
            }

            if (!datacenterChannel.verify(response)) {
                logger.error("[Client {}] Falha ao verificar resposta do Datacenter", clientId);
                return null;
            }

            EnvelopeTCP envelope = datacenterChannel.decryptEnvelope("DATACENTER", response);
            if (envelope == null) {
                logger.error("[Client {}] Falha ao decifrar envelope do Datacenter", clientId);
                return null;
            }

            if (envelope.getType() == MessageTypeTCP.ERROR) {
                logger.warn("[Client {}] Erro do Datacenter: {}", clientId, envelope.getPayload());
                return null;
            }

            if (envelope.getType() != MessageTypeTCP.QUERY_RESPONSE) {
                logger.error("[Client {}] Resposta inesperada: {}", clientId, envelope.getType());
                return null;
            }

            logger.debug("[Client {}] Relatório recebido: {} bytes", clientId, envelope.getPayload().length());
            return envelope.getPayload();

        } catch (Exception e) {
            logger.error("[Client {}] Erro na consulta: {}", clientId, e.getMessage());
            return null;
        }
    }

    // ==================== HANDSHAKE ====================

    private boolean performHandshake(SecureTCPChannel channel, String peerId) {
        MessageTCP hello = channel.buildHello();
        channel.send(hello);
        logger.debug("[Client {}] HELLO enviado para {}", clientId, peerId);

        MessageTCP challenge = channel.receive();
        if (challenge == null || challenge.getType() != MessageTypeTCP.CHALLENGE) {
            logger.error("[Client {}] Esperava CHALLENGE de {}, recebeu: {}",
                clientId, peerId, challenge != null ? challenge.getType() : "null");
            return false;
        }
        logger.debug("[Client {}] CHALLENGE recebido de {}", clientId, peerId);

        if (!channel.handleChallenge(challenge)) {
            logger.error("[Client {}] Falha ao processar CHALLENGE de {}", clientId, peerId);
            return false;
        }

        logger.info("[Client {}] Handshake com {} concluído", clientId, peerId);
        return true;
    }

    // ==================== CLEANUP ====================

    private void closeAuthChannel() {
        if (authChannel != null) {
            authChannel.clearPeerSession("AUTH");
            authChannel.close();
            authChannel = null;
        }
    }

    public void closeDatacenterChannel() {
        if (datacenterChannel != null) {
            datacenterChannel.clearPeerSession("DATACENTER");
            datacenterChannel.close();
            datacenterChannel = null;
        }
    }

    public void disconnect() {
        closeAuthChannel();
        closeDatacenterChannel();
        this.jwtToken = null;
    }

    public boolean isConnectedToDatacenter() {
        return datacenterChannel != null;
    }

    public boolean isAuthenticated() {
        return jwtToken != null;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public void clearToken() {
        this.jwtToken = null;
    }
}
