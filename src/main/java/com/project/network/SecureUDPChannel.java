package com.project.network;

import java.io.IOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import java.security.GeneralSecurityException;

import java.util.Base64;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.AES;
import com.project.crypto.KeyManager;

import com.project.message.udp.EnvelopeUDP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;

public class SecureUDPChannel {
    private static final int BUFFER_SIZE = 4096;
    private final Logger logger;
    private final String entityId;
    private final KeyManager keyManager;
    private final DatagramSocket socket;
    public SecureUDPChannel(String entityId, KeyManager keyManager, DatagramSocket socket) {
        this.entityId = entityId;
        this.keyManager = keyManager;
        this.socket = socket;
        this.logger = LoggerFactory.getLogger("Channel:" + entityId);
    }
    // ==================== ENVIO ====================
    public void send(MessageUDP message, InetAddress address, int port) {
        try {
            byte[] data = message.toJson().getBytes();
            socket.send(new DatagramPacket(data, data.length, address, port));
            logger.debug("Mensagem {} enviada para {}:{}", message.getType(), address.getHostAddress(), port);
        } catch (IOException e) {
            logger.error("Erro ao enviar para {}:{} - {}", address.getHostAddress(), port, e.getMessage());
        }
    }
    public void send(MessageUDP message, String host, int port) {
        try {
            send(message, InetAddress.getByName(host), port);
        } catch (IOException e) {
            logger.error("Host desconhecido: {}", host);
        }
    }
    // ==================== RECEBIMENTO ====================
    public ReceivedPacket receive() {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String json = new String(packet.getData(), 0, packet.getLength());
            return new ReceivedPacket(MessageUDP.fromJson(json), packet.getAddress(), packet.getPort());
        } catch (SocketTimeoutException e) {
            logger.warn("Timeout aguardando mensagem");
            return null;
        } catch (IOException e) {
            logger.error("Erro ao receber: {}", e.getMessage());
            return null;
        }
    }
    // ==================== MENSAGENS CIFRADAS ====================
    public MessageUDP buildEncrypted(String peerId, MessageTypeUDP type, String payload) {
        try {
            SecretKey aesKey = keyManager.getPeerAESKey(peerId);
            AES aes = new AES(aesKey);
            String encryptedPayload = aes.encrypt(payload);
            // Assinar ciphertext diretamente (sem HMAC)
            String signature = keyManager.signBase64(encryptedPayload.getBytes());
            return MessageUDP.builder()
                    .type(type)
                    .senderId(entityId)
                    .encryptedPayload(encryptedPayload)
                    .signature(signature)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao construir mensagem cifrada para '{}': {}", peerId, e.getMessage());
            return null;
        }
    }
    public boolean verify(MessageUDP message) {
        String senderId = message.getSenderId();
        if (!keyManager.hasSessionKeys(senderId)) {
            logger.warn("Sem chaves de sessão para '{}' - handshake necessário", senderId);
            return false;
        }
        try {
            // Verificar assinatura RSA do ciphertext (autenticidade + integridade)
            byte[] ciphertextBytes = message.getEncryptedPayload().getBytes();
            byte[] signatureBytes = Base64.getDecoder().decode(message.getSignature());
            if (!keyManager.verifySignature(senderId, ciphertextBytes, signatureBytes)) {
                logger.warn("Assinatura inválida de '{}'", senderId);
                return false;
            }
            return true;
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao verificar mensagem de '{}': {}", senderId, e.getMessage());
            return false;
        }
    }
    public String decrypt(String peerId, MessageUDP message) {
        try {
            AES aes = new AES(keyManager.getPeerAESKey(peerId));
            return aes.decrypt(message.getEncryptedPayload());
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao decifrar mensagem de '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    // ==================== ENVELOPE CIFRADO ====================
    
    public MessageUDP buildEncryptedEnvelope(String peerId, MessageTypeUDP type, String payload, String jwtToken) {
        try {
            // Passo 1 - Criar envelope com dados sensíveis
            EnvelopeUDP envelope = EnvelopeUDP.builder()
                    .type(type)
                    .payload(payload)
                    .jwtToken(jwtToken)
                    .nonce(java.util.UUID.randomUUID().toString())
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Passo 2 - Cifrar envelope com AES
            SecretKey aesKey = keyManager.getPeerAESKey(peerId);
            AES aes = new AES(aesKey);

            String envelopeJson = envelope.toJson();
            String encryptedEnvelope = aes.encrypt(envelopeJson);

            // Passo 3 - Assinar ciphertext diretamente (sem HMAC)
            String signature = keyManager.signBase64(encryptedEnvelope.getBytes());

            // Passo 4 - Construir mensagem externa (type=null indica envelope cifrado)
            return MessageUDP.builder()
                    .type(null)
                    .senderId(entityId)
                    .encryptedPayload(encryptedEnvelope)
                    .signature(signature)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao construir envelope cifrado para '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public MessageUDP buildEncryptedEnvelope(String peerId, MessageTypeUDP type, String payload) {
        return buildEncryptedEnvelope(peerId, type, payload, null);
    }

    public EnvelopeUDP decryptEnvelope(String peerId, MessageUDP message) {
        try {
            AES aes = new AES(keyManager.getPeerAESKey(peerId));
            String envelopeJson = aes.decrypt(message.getEncryptedPayload());
            return EnvelopeUDP.fromJson(envelopeJson);
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao decifrar envelope de '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public boolean isEncryptedEnvelope(MessageUDP message) {
        return message.getType() == null && message.getEncryptedPayload() != null;
    }

    // ==================== HANDSHAKE ====================
    public MessageUDP buildHello() {
        return MessageUDP.builder()
                .type(MessageTypeUDP.HELLO)
                .senderId(entityId)
                .senderPublicKey(keyManager.getPublicKeyBase64())
                .build();
    }
    public MessageUDP handleHello(MessageUDP hello) {
        String peerId = hello.getSenderId();
        try {
            keyManager.storePeerKey(peerId, hello.getSenderPublicKey());
            keyManager.generateSessionKeys(peerId);
            String encryptedKeys = keyManager.encryptSessionKeysForPeer(peerId);
            return MessageUDP.builder()
                    .type(MessageTypeUDP.CHALLENGE)
                    .senderId(entityId)
                    .senderPublicKey(keyManager.getPublicKeyBase64())
                    .encryptedSessionKeys(encryptedKeys)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao processar HELLO de '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public boolean handleChallenge(MessageUDP challenge) {
        String peerId = challenge.getSenderId();
        try {
            keyManager.storePeerKey(peerId, challenge.getSenderPublicKey());
            keyManager.decryptAndStoreSessionKeys(peerId, challenge.getEncryptedSessionKeys());
            return true;
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao processar CHALLENGE de '{}': {}", peerId, e.getMessage());
            return false;
        }
    }
    // ==================== GETTERS ====================
    public String getEntityId() { return entityId; }
    public KeyManager getKeyManager() { return keyManager; }
    public DatagramSocket getSocket() { return socket; }

    public void clearPeerSession(String peerId) {
        keyManager.clearPeerKeys(peerId);
    }

    // ==================== RECORD ====================
    public record ReceivedPacket(MessageUDP message, InetAddress address, int port) {}
}