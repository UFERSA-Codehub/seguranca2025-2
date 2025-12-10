package com.project.server.firewall;

import java.io.IOException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;

public class IdsClient {
    private static final Logger logger = LoggerFactory.getLogger("IdsClient");

    private final String firewallId;
    private final String idsHost;
    private final int idsPort;
    private final KeyManager keyManager;

    private Socket socket;
    private SecureTCPChannel channel;
    private volatile boolean connected;

    public IdsClient(String firewallId, KeyManager keyManager, String idsHost, int idsPort) {
        this.firewallId = firewallId;
        this.keyManager = keyManager;
        this.idsHost = idsHost;
        this.idsPort = idsPort;
        this.connected = false;
    }

    public synchronized boolean connect() {
        if (connected && socket != null && !socket.isClosed()) {
            return true;
        }

        logger.info("Conectando ao IDS em {}:{}...", idsHost, idsPort);

        try {
            socket = new Socket(idsHost, idsPort);
            socket.setSoTimeout(10000);
            channel = new SecureTCPChannel(firewallId, keyManager, socket);

            // Handshake
            MessageTCP hello = channel.buildHello();
            channel.send(hello);

            MessageTCP challenge = channel.receive();
            if (challenge == null || challenge.getType() != MessageTypeTCP.CHALLENGE) {
                logger.error("Esperava CHALLENGE do IDS, recebeu: {}", 
                           challenge != null ? challenge.getType() : "null");
                disconnect();
                return false;
            }

            if (!channel.handleChallenge(challenge)) {
                logger.error("Falha ao processar CHALLENGE do IDS");
                disconnect();
                return false;
            }

            connected = true;
            logger.info("Conectado ao IDS com sucesso");
            return true;

        } catch (IOException e) {
            logger.error("Falha ao conectar ao IDS: {}", e.getMessage());
            disconnect();
            return false;
        }
    }

    public synchronized void sendAlert(String sourceIp, int sourcePort, String destService,
                                        String alertType, String content) {
        if (!ensureConnected()) {
            logger.warn("Nao foi possivel enviar alerta - IDS indisponivel");
            return;
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("sourceIp", sourceIp);
            payload.addProperty("sourcePort", sourcePort);
            payload.addProperty("destService", destService);
            payload.addProperty("alertType", alertType);
            payload.addProperty("content", content);

            MessageTCP alertMsg = channel.buildEncrypted("IDS", MessageTypeTCP.ALERT, payload.toString());
            if (alertMsg != null) {
                channel.send(alertMsg);
                logger.info("Alerta [{}] enviado ao IDS: {} -> {}", alertType, sourceIp, destService);

                // Aguardar ACK (com timeout curto)
                socket.setSoTimeout(2000);
                MessageTCP ack = channel.receive();
                if (ack != null) {
                    logger.debug("ACK recebido do IDS");
                }
                socket.setSoTimeout(10000);
            }

        } catch (Exception e) {
            logger.error("Erro ao enviar alerta: {}", e.getMessage());
            disconnect();
        }
    }

    private boolean ensureConnected() {
        if (connected && socket != null && !socket.isClosed()) {
            return true;
        }
        return connect();
    }

    public synchronized void disconnect() {
        connected = false;
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.debug("Erro ao fechar socket: {}", e.getMessage());
            }
            socket = null;
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}
