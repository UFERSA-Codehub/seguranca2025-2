package com.project.server.edge;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.message.tcp.EnvelopeTCP;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;
import com.project.server.edge.data.Cache.CacheEntry;

public class TcpClient {
    private static final Logger logger = LoggerFactory.getLogger("Edge.TcpClient");

    // Chave secreta compartilhada entre Edge e Datacenter
    private static final String EDGE_SECRET = "EdgeDatacenterSharedSecret2025";

    private final String edgeId;
    private final String datacenterHost;
    private final int datacenterPort;
    private SecureTCPChannel channel;
    private volatile boolean connected;
    private String peerId; // ID do peer (pode ser DATACENTER ou DATACENTER_EDGE via proxy)

    public TcpClient(String edgeId, String datacenterHost, int datacenterPort) {
        this.edgeId = edgeId;
        this.datacenterHost = datacenterHost;
        this.datacenterPort = datacenterPort;
        this.connected = false;
    }

    public boolean connect() {
        if (connected && channel != null) {
            return true;
        }

        String peerInfo = datacenterHost + ":" + datacenterPort;
        logger.info("Conectando ao Datacenter ({})...", peerInfo);

        try {
            Socket socket = new Socket(datacenterHost, datacenterPort);
            KeyManager keyManager = new KeyManager();
            this.channel = new SecureTCPChannel(edgeId, keyManager, socket);
            channel.setTracePeerId("DATACENTER");

            // Passo 1 - Enviar HELLO
            channel.send(channel.buildHello());
        logger.debug("HELLO enviado para DATACENTER ({})", peerInfo);

            // Passo 2 - Receber CHALLENGE
            MessageTCP challenge = channel.receive();
            if (challenge == null || challenge.getType() != MessageTypeTCP.CHALLENGE) {
                logger.error("Resposta inesperada do Datacenter: {}", challenge != null ? challenge.getType() : "null");
                return false;
            }
            
            // Extrair o sender ID do CHALLENGE (pode ser DATACENTER ou DATACENTER_EDGE via proxy)
            this.peerId = challenge.getSenderId();
            logger.debug("CHALLENGE recebido de {} ({})", peerId, peerInfo);
            channel.setTracePeerId(peerId);

            if (!channel.handleChallenge(challenge)) {
                logger.error("Falha ao processar CHALLENGE do Datacenter");
                return false;
            }

            // Passo 3 - Enviar EDGE_AUTH com envelope cifrado
            JsonObject authPayload = new JsonObject();
            authPayload.addProperty("edgeId", edgeId);
            authPayload.addProperty("secret", EDGE_SECRET);

            MessageTCP authMsg = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.EDGE_AUTH, authPayload.toString());
            if (authMsg == null) {
                logger.error("Falha ao construir mensagem EDGE_AUTH");
                return false;
            }
            channel.send(authMsg);
            logger.debug("EDGE_AUTH enviado para DATACENTER");

            // Passo 4 - Receber resposta de autenticação (envelope cifrado)
            MessageTCP authResponse = channel.receive();
            if (authResponse == null) {
                logger.error("Timeout aguardando resposta de autenticação");
                return false;
            }

            // Verificar e decifrar envelope
            if (!channel.verify(authResponse)) {
                logger.error("Falha ao verificar resposta de autenticação");
                return false;
            }

            EnvelopeTCP envelope = channel.decryptEnvelope(peerId, authResponse);
            if (envelope == null) {
                logger.error("Falha ao decifrar envelope de autenticação");
                return false;
            }

            if (envelope.getType() == MessageTypeTCP.EDGE_AUTH_FAIL) {
                logger.error("Autenticação rejeitada pelo Datacenter: {}", envelope.getPayload());
                return false;
            }

            if (envelope.getType() != MessageTypeTCP.EDGE_AUTH_OK) {
                logger.error("Resposta inesperada do Datacenter: {}", envelope.getType());
                return false;
            }

            this.connected = true;
            logger.info("Autenticado no Datacenter ({})", peerInfo);
            return true;

        } catch (IOException e) {
            logger.error("Erro ao conectar ao Datacenter ({}): {}", peerInfo, e.getMessage());
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

    public boolean sendBatch(List<CacheEntry> entries) {
        if (!connected || channel == null) {
            logger.warn("Não conectado ao Datacenter");
            return false;
        }

        // Construir payload
        JsonObject payload = new JsonObject();
        JsonArray readings = new JsonArray();
        for (CacheEntry entry : entries) {
            readings.add(entry.toJson());
        }
        payload.add("readings", readings);

        // Enviar DATA_BATCH com envelope cifrado
        MessageTCP message = channel.buildEncryptedEnvelope(peerId, MessageTypeTCP.DATA_BATCH, payload.toString());
        if (message == null) {
            logger.error("Falha ao construir DATA_BATCH");
            return false;
        }
        channel.send(message);
        logger.info("DATA_BATCH enviado: {} leituras", entries.size());

        // Receber resposta (envelope cifrado)
        MessageTCP response = channel.receive();
        if (response == null) {
            logger.error("Timeout aguardando DATA_ACK - conexão perdida");
            markDisconnected();
            return false;
        }

        // Verificar e decifrar envelope
        if (!channel.verify(response)) {
            logger.error("Falha ao verificar resposta DATA_ACK");
            return false;
        }

        EnvelopeTCP envelope = channel.decryptEnvelope(peerId, response);
        if (envelope == null) {
            logger.error("Falha ao decifrar envelope DATA_ACK");
            return false;
        }

        if (envelope.getType() == MessageTypeTCP.DATA_ACK) {
            logger.info("DATA_ACK recebido");
            return true;
        } else if (envelope.getType() == MessageTypeTCP.ERROR) {
            logger.error("Erro do Datacenter: {}", envelope.getPayload());
            return false;
        }

        logger.warn("Resposta inesperada: {}", envelope.getType());
        return false;
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
        logger.info("Desconectado do Datacenter");
    }

    public boolean isConnected() {
        return connected;
    }
}
