package com.project.server.ids;

import java.io.IOException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;
import com.project.server.ids.AlertStore.Alert;

public class TcpHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("IDS:TcpHandler");
    private static final Gson gson = new Gson();

    private static final int ANOMALY_THRESHOLD = 2;
    private static final long ANOMALY_WINDOW_MS = 60_000;

    private final Socket clientSocket;
    private final KeyManager keyManager;
    private final AlertStore alertStore;
    private final ServerIDS serverIDS;

    public TcpHandler(Socket clientSocket, KeyManager keyManager, AlertStore alertStore, ServerIDS serverIDS) {
        this.clientSocket = clientSocket;
        this.keyManager = keyManager;
        this.alertStore = alertStore;
        this.serverIDS = serverIDS;
    }

    @Override
    public void run() {
        String clientId = clientSocket.getRemoteSocketAddress().toString();
        logger.info("Nova conexao de firewall: {}", clientId);

        try {
            SecureTCPChannel channel = new SecureTCPChannel("IDS", keyManager, clientSocket);
            
            // Handshake
            MessageTCP hello = channel.receive();
            if (hello == null || hello.getType() != MessageTypeTCP.HELLO) {
                logger.warn("Esperava HELLO, recebeu: {}", hello != null ? hello.getType() : "null");
                return;
            }

            MessageTCP challenge = channel.handleHello(hello);
            if (challenge == null) {
                logger.error("Falha ao processar HELLO");
                return;
            }
            channel.send(challenge);

            String peerId = hello.getSenderId();
            logger.info("Handshake concluido com {}", peerId);

            // Loop de recebimento de alertas
            while (!clientSocket.isClosed()) {
                MessageTCP message = channel.receive();
                if (message == null) {
                    logger.debug("Conexao fechada por {}", peerId);
                    break;
                }

                if (!channel.verify(message)) {
                    logger.warn("Mensagem invalida de {}", peerId);
                    continue;
                }

                handleMessage(channel, peerId, message);
            }

        } catch (IOException e) {
            logger.error("Erro na conexao com {}: {}", clientId, e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.debug("Erro ao fechar socket: {}", e.getMessage());
            }
        }
    }

    private void handleMessage(SecureTCPChannel channel, String peerId, MessageTCP message) {
        if (channel.isEncryptedEnvelope(message)) {
            var envelope = channel.decryptEnvelope(peerId, message);
            if (envelope == null) {
                logger.warn("Falha ao decifrar envelope de {}", peerId);
                return;
            }

            if (envelope.getType() == MessageTypeTCP.ALERT) {
                handleAlert(channel, peerId, envelope.getPayload());
            }
        } else if (message.getType() == MessageTypeTCP.ALERT) {
            String payload = channel.decrypt(peerId, message);
            if (payload != null) {
                handleAlert(channel, peerId, payload);
            }
        }
    }

    private void handleAlert(SecureTCPChannel channel, String peerId, String payload) {
        try {
            JsonObject alertData = gson.fromJson(payload, JsonObject.class);

            String sourceIp = alertData.get("sourceIp").getAsString();
            int sourcePort = alertData.get("sourcePort").getAsInt();
            String destService = alertData.get("destService").getAsString();
            String alertType = alertData.get("alertType").getAsString();
            String content = alertData.has("content") ? alertData.get("content").getAsString() : "";

            Alert alert = Alert.of(sourceIp, sourcePort, destService, alertType, content);
            alertStore.store(alert);

            logger.warn("ALERTA [{}] de {} para {}: {}", alertType, sourceIp, destService, content);

            // Enviar ACK
            MessageTCP ack = channel.buildEncrypted(peerId, MessageTypeTCP.ALERT_ACK, "{\"status\":\"received\"}");
            if (ack != null) {
                channel.send(ack);
            }

            // Verificar se deve disparar TERMINATE
            if (shouldTerminate(sourceIp, alertType)) {
                logger.warn("IP {} excedeu limite de alertas - enviando TERMINATE para Edge", sourceIp);
                serverIDS.sendTerminateToEdge(sourceIp);
            }

        } catch (Exception e) {
            logger.error("Erro ao processar alerta: {}", e.getMessage());
        }
    }

    private boolean shouldTerminate(String sourceIp, String alertType) {
        if ("BLOCKED".equals(alertType) || "PORT_SCAN".equals(alertType)) {
            return true;
        }

        if ("ANOMALY".equals(alertType) || "RATE_LIMIT".equals(alertType)) {
            int recentAlerts = alertStore.countByIp(sourceIp, ANOMALY_WINDOW_MS);
            return recentAlerts >= ANOMALY_THRESHOLD;
        }

        return false;
    }
}
