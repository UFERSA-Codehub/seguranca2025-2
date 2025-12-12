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
import com.project.tracing.TraceEvent;
import com.project.tracing.TracerFactory;

public class TcpHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("IDS.TcpHandler");
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

            String peerId = hello.getSenderId();
            // Definir tracePeerId logo apos saber o peerId, antes de enviar qualquer resposta
            channel.setTracePeerId(peerId);

            MessageTCP challenge = channel.handleHello(hello);
            if (challenge == null) {
                logger.error("Falha ao processar HELLO");
                return;
            }
            channel.send(challenge);
            logger.info("Handshake concluído com {}", peerId);

            // Loop de recebimento de alertas
            while (!clientSocket.isClosed()) {
                MessageTCP message = channel.receive();
                if (message == null) {
                    logger.info("Conexao fechada por {}", peerId);
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
            TracerFactory.getTracer().trace(TraceEvent.create(
                "IDS",
                "TCP",
                "RECEIVE",
                clientSocket.getRemoteSocketAddress().toString(),
                null,
                "ALERT",
                null,
                payload,
                peerId
            ));

            JsonObject alertData = gson.fromJson(payload, JsonObject.class);

            String sourceIp = alertData.get("sourceIp").getAsString();
            int sourcePort = alertData.get("sourcePort").getAsInt();
            String destService = alertData.get("destService").getAsString();
            String alertType = alertData.get("alertType").getAsString();
            String content = alertData.has("content") ? alertData.get("content").getAsString() : "";
            String sensorId = alertData.has("sensorId") ? alertData.get("sensorId").getAsString() : null;

            Alert alert = Alert.of(sourceIp, sourcePort, destService, alertType, content, sensorId);
            alertStore.store(alert);

            String sensorInfo = sensorId != null ? " (sensor: " + sensorId + ")" : "";
            logger.warn("ALERTA [{}] de {} para {}{}: {}", alertType, sourceIp, destService, sensorInfo, content);

            // Enviar ACK
            MessageTCP ack = channel.buildEncrypted(peerId, MessageTypeTCP.ALERT_ACK, "{\"status\":\"received\"}");
            if (ack != null) {
                channel.send(ack);
            }

            // Verificar se deve disparar TERMINATE
            if (shouldTerminate(sourceIp, sensorId, alertType)) {
                if (sensorId != null) {
                    logger.warn("Sensor '{}' excedeu limite de alertas - enviando TERMINATE para Edge", sensorId);
                    serverIDS.sendTerminateToEdge(sourceIp, sensorId);
                } else {
                    logger.warn("IP {} excedeu limite de alertas - enviando TERMINATE para Edge", sourceIp);
                    serverIDS.sendTerminateToEdge(sourceIp, null);
                }
            }

        } catch (Exception e) {
            logger.error("Erro ao processar alerta: {}", e.getMessage());
        }
    }

    private boolean shouldTerminate(String sourceIp, String sensorId, String alertType) {
        if ("BLOCKED".equals(alertType) || "PORT_SCAN".equals(alertType)) {
            return true;
        }

        if ("ANOMALY".equals(alertType) || "RATE_LIMIT".equals(alertType)) {
            // Preferir contar por sensor ID se disponível
            int recentAlerts;
            if (sensorId != null) {
                recentAlerts = alertStore.countBySensorId(sensorId, ANOMALY_WINDOW_MS);
            } else {
                recentAlerts = alertStore.countByIp(sourceIp, ANOMALY_WINDOW_MS);
            }
            return recentAlerts >= ANOMALY_THRESHOLD;
        }

        return false;
    }
}
