package com.project.client.sensor;

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

public class TcpClient {
    private static final Logger logger = LoggerFactory.getLogger("Sensor.TcpClient");
    private static final Gson gson = new Gson();
    private static final int SOCKET_TIMEOUT_MS = 10_000;

    private final String sensorId;
    private final KeyManager keyManager;

    private SecureTCPChannel authChannel;
    private SecureTCPChannel edgeChannel;

    public TcpClient(String sensorId, KeyManager keyManager) {
        this.sensorId = sensorId;
        this.keyManager = keyManager;
    }

    public String authenticateWithAuthServer(String authHost, int authPort, String password) {
        logger.info("[Sensor {}] Conectando ao AuthServer ({}:{})...", sensorId, authHost, authPort);

        try {
            Socket socket = new Socket(authHost, authPort);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            this.authChannel = new SecureTCPChannel(sensorId, keyManager, socket);

            if (!performHandshake(authChannel, "AUTH")) {
                logger.error("[Sensor {}] Falha no handshake com AuthServer", sensorId);
                closeAuthChannel();
                return null;
            }

            JsonObject authPayload = new JsonObject();
            authPayload.addProperty("sensorId", sensorId);
            authPayload.addProperty("password", password);

            MessageTCP authMsg = authChannel.buildEncryptedEnvelope("AUTH", MessageTypeTCP.AUTH, authPayload.toString());
            if (authMsg == null) {
                logger.error("[Sensor {}] Falha ao construir mensagem AUTH", sensorId);
                closeAuthChannel();
                return null;
            }
            authChannel.send(authMsg);

            MessageTCP response = authChannel.receive();
            if (response == null) {
                logger.error("[Sensor {}] Timeout aguardando resposta de autenticacao", sensorId);
                closeAuthChannel();
                return null;
            }

            if (!authChannel.verify(response)) {
                logger.error("[Sensor {}] Falha ao verificar resposta de autenticacao", sensorId);
                closeAuthChannel();
                return null;
            }

            EnvelopeTCP envelope = authChannel.decryptEnvelope("AUTH", response);
            if (envelope == null) {
                logger.error("[Sensor {}] Falha ao decifrar envelope de autenticacao", sensorId);
                closeAuthChannel();
                return null;
            }

            if (envelope.getType() == MessageTypeTCP.AUTH_FAIL) {
                logger.error("[Sensor {}] Autenticacao falhou: {}", sensorId, envelope.getPayload());
                closeAuthChannel();
                return null;
            }

            String token = gson.fromJson(envelope.getPayload(), JsonObject.class).get("token").getAsString();
            logger.info("[Sensor {}] Autenticado com sucesso no AuthServer", sensorId);

            closeAuthChannel();
            return token;

        } catch (IOException e) {
            logger.error("[Sensor {}] Erro de conexao com AuthServer: {}", sensorId, e.getMessage());
            return null;
        }
    }

    public boolean connectToEdge(String edgeHost, int edgePort) {
        logger.info("[Sensor {}] Conectando ao Edge ({}:{})...", sensorId, edgeHost, edgePort);

        try {
            Socket socket = new Socket(edgeHost, edgePort);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            this.edgeChannel = new SecureTCPChannel(sensorId, keyManager, socket);

            if (!performHandshake(edgeChannel, "EDGE")) {
                logger.error("[Sensor {}] Falha no handshake com Edge", sensorId);
                closeEdgeChannel();
                return false;
            }

            logger.info("[Sensor {}] Conectado ao Edge", sensorId);
            return true;

        } catch (IOException e) {
            logger.error("[Sensor {}] Erro de conexao com Edge: {}", sensorId, e.getMessage());
            return false;
        }
    }

    public boolean sendData(String dataJson, String jwtToken) {
        if (edgeChannel == null) {
            logger.error("[Sensor {}] Nao conectado ao Edge", sensorId);
            return false;
        }

        MessageTCP dataMsg = edgeChannel.buildEncryptedEnvelope("EDGE", MessageTypeTCP.DATA, dataJson, jwtToken);
        if (dataMsg == null) {
            logger.error("[Sensor {}] Falha ao construir mensagem DATA", sensorId);
            return false;
        }
        edgeChannel.send(dataMsg);

        MessageTCP response = edgeChannel.receive();
        if (response == null) {
            logger.warn("[Sensor {}] Timeout aguardando DATA_OK", sensorId);
            return false;
        }

        if (!edgeChannel.verify(response)) {
            logger.warn("[Sensor {}] Falha ao verificar DATA_OK", sensorId);
            return false;
        }

        EnvelopeTCP envelope = edgeChannel.decryptEnvelope("EDGE", response);
        if (envelope == null) {
            logger.warn("[Sensor {}] Falha ao decifrar DATA_OK", sensorId);
            return false;
        }

        if (envelope.getType() != MessageTypeTCP.DATA_OK) {
            logger.warn("[Sensor {}] Resposta inesperada: {}", sensorId, envelope.getType());
            return false;
        }

        return true;
    }

    private boolean performHandshake(SecureTCPChannel channel, String peerId) {
        MessageTCP hello = channel.buildHello();
        channel.send(hello);
        logger.debug("[Sensor {}] HELLO enviado para {}", sensorId, peerId);

        MessageTCP challenge = channel.receive();
        if (challenge == null || challenge.getType() != MessageTypeTCP.CHALLENGE) {
            logger.error("[Sensor {}] Esperava CHALLENGE de {}, recebeu: {}", 
                sensorId, peerId, challenge != null ? challenge.getType() : "null");
            return false;
        }
        logger.debug("[Sensor {}] CHALLENGE recebido de {}", sensorId, peerId);

        if (!channel.handleChallenge(challenge)) {
            logger.error("[Sensor {}] Falha ao processar CHALLENGE de {}", sensorId, peerId);
            return false;
        }

        logger.info("[Sensor {}] Handshake com {} concluido", sensorId, peerId);
        return true;
    }

    private void closeAuthChannel() {
        if (authChannel != null) {
            authChannel.clearPeerSession("AUTH");
            authChannel.close();
            authChannel = null;
        }
    }

    public void closeEdgeChannel() {
        if (edgeChannel != null) {
            edgeChannel.clearPeerSession("EDGE");
            edgeChannel.close();
            edgeChannel = null;
        }
    }

    public boolean isConnectedToEdge() {
        return edgeChannel != null && !edgeChannel.getSocket().isClosed();
    }
}
