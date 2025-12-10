package com.project.server.edge;

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

public class IdsCommandHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("Edge.IdsCommandHandler");
    private static final Gson gson = new Gson();

    private final Socket idsSocket;
    private final KeyManager keyManager;
    private final ServerEdge serverEdge;

    public IdsCommandHandler(Socket idsSocket, KeyManager keyManager, ServerEdge serverEdge) {
        this.idsSocket = idsSocket;
        this.keyManager = keyManager;
        this.serverEdge = serverEdge;
    }

    @Override
    public void run() {
        String clientId = idsSocket.getRemoteSocketAddress().toString();
        logger.debug("Handler IDS iniciado para: {}", clientId);

        try {
            SecureTCPChannel channel = new SecureTCPChannel("EDGE", keyManager, idsSocket);

            MessageTCP hello = channel.receive();
            if (hello == null || hello.getType() != MessageTypeTCP.HELLO) {
                logger.warn("Esperava HELLO do IDS, recebeu: {}", hello != null ? hello.getType() : "null");
                return;
            }

            MessageTCP challenge = channel.handleHello(hello);
            if (challenge == null) {
                logger.error("Falha ao processar HELLO do IDS");
                return;
            }
            channel.send(challenge);

            String peerId = hello.getSenderId();
            logger.debug("Handshake com IDS concluído ({})", peerId);

            while (!idsSocket.isClosed()) {
                MessageTCP message = channel.receive();
                if (message == null) {
                    logger.debug("Conexão IDS fechada");
                    break;
                }

                if (!channel.verify(message)) {
                    logger.warn("Mensagem inválida do IDS");
                    continue;
                }

                handleMessage(channel, peerId, message);
            }

        } catch (IOException e) {
            logger.error("Erro na conexão com IDS: {}", e.getMessage());
        } finally {
            try {
                idsSocket.close();
            } catch (IOException e) {
                logger.debug("Erro ao fechar socket IDS: {}", e.getMessage());
            }
        }
    }

    private void handleMessage(SecureTCPChannel channel, String peerId, MessageTCP message) {
        if (channel.isEncryptedEnvelope(message)) {
            EnvelopeTCP envelope = channel.decryptEnvelope(peerId, message);
            if (envelope == null) {
                logger.warn("Falha ao decifrar envelope do IDS");
                return;
            }

            if (envelope.getType() == MessageTypeTCP.TERMINATE) {
                handleTerminate(channel, peerId, envelope.getPayload());
            } else {
                logger.warn("Tipo de mensagem não suportado do IDS: {}", envelope.getType());
            }
        } else if (message.getType() == MessageTypeTCP.TERMINATE) {
            String payload = channel.decrypt(peerId, message);
            if (payload != null) {
                handleTerminate(channel, peerId, payload);
            }
        }
    }

    private void handleTerminate(SecureTCPChannel channel, String peerId, String payload) {
        try {
            JsonObject data = gson.fromJson(payload, JsonObject.class);
            String targetIp = data.get("targetIp").getAsString();

            logger.warn("Comando TERMINATE recebido do IDS para IP: {}", targetIp);

            serverEdge.terminateByIp(targetIp);

            JsonObject response = new JsonObject();
            response.addProperty("status", "terminated");
            response.addProperty("ip", targetIp);

            MessageTCP ack = channel.buildEncrypted(peerId, MessageTypeTCP.TERMINATE_ACK, response.toString());
            if (ack != null) {
                channel.send(ack);
                logger.info("TERMINATE_ACK enviado para IDS");
            }

        } catch (Exception e) {
            logger.error("Erro ao processar TERMINATE: {}", e.getMessage());
        }
    }
}
