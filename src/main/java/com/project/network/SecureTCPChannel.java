package com.project.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.AES;
import com.project.crypto.HMAC;
import com.project.crypto.KeyManager;
import com.project.message.tcp.EnvelopeTCP;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;

public class SecureTCPChannel {
    private final Logger logger;
    private final String entityId;
    private final KeyManager keyManager;
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;

    public SecureTCPChannel(String entityId, KeyManager keyManager, Socket socket) throws IOException {
        this.entityId = entityId;
        this.keyManager = keyManager;
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.logger = LoggerFactory.getLogger("TCPChannel:" + entityId);
    }

    // ==================== ENVIO ====================

    public void send(MessageTCP message) {
        try {
            String json = message.toJson();
            writer.println(json);
            logger.debug("Mensagem {} enviada", message.getType());
        } catch (Exception e) {
            logger.error("Erro ao enviar mensagem: {}", e.getMessage());
        }
    }

    // ==================== RECEBIMENTO ====================

    public MessageTCP receive() {
        try {
            String json = reader.readLine();
            if (json == null) {
                logger.debug("Conexão fechada pelo peer");
                return null;
            }
            MessageTCP message = MessageTCP.fromJson(json);
            logger.debug("Mensagem {} recebida de {}", message.getType(), message.getSenderId());
            return message;
        } catch (SocketTimeoutException e) {
            logger.warn("Timeout aguardando mensagem");
            return null;
        } catch (IOException e) {
            logger.error("Erro ao receber mensagem: {}", e.getMessage());
            return null;
        }
    }

    // ==================== MENSAGENS CIFRADAS ====================

    public MessageTCP buildEncrypted(String peerId, MessageTypeTCP type, String payload) {
        try {
            SecretKey aesKey = keyManager.getPeerAESKey(peerId);
            SecretKey hmacKey = keyManager.getPeerHMACKey(peerId);
            
            AES aes = new AES(aesKey);
            HMAC hmac = new HMAC(hmacKey);

            String encryptedPayload = aes.encrypt(payload);
            String hmacValue = hmac.sign(encryptedPayload);
            String signature = keyManager.signBase64(encryptedPayload.getBytes());

            return MessageTCP.builder()
                    .type(type)
                    .senderId(entityId)
                    .encryptedPayload(encryptedPayload)
                    .hmac(hmacValue)
                    .signature(signature)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao construir mensagem cifrada para '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public boolean verify(MessageTCP message) {
        String senderId = message.getSenderId();

        if (!keyManager.hasSessionKeys(senderId)) {
            logger.warn("Sem chaves de sessão para '{}' - handshake necessário", senderId);
            return false;
        }

        try {
            String encryptedPayload = message.getEncryptedPayload();
            
            // Passo 1 - Verificar HMAC (integridade simétrica)
            SecretKey hmacKey = keyManager.getPeerHMACKey(senderId);
            HMAC hmac = new HMAC(hmacKey);
            if (!hmac.verify(encryptedPayload, message.getHmac())) {
                logger.warn("HMAC inválido de '{}'", senderId);
                return false;
            }
            
            // Passo 2 - Verificar assinatura RSA (autenticidade)
            byte[] ciphertextBytes = encryptedPayload.getBytes();
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

    public String decrypt(String peerId, MessageTCP message) {
        try {
            AES aes = new AES(keyManager.getPeerAESKey(peerId));
            return aes.decrypt(message.getEncryptedPayload());
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao decifrar mensagem de '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    // ==================== ENVELOPE CIFRADO ====================

    public MessageTCP buildEncryptedEnvelope(String peerId, MessageTypeTCP type, String payload, String jwtToken) {
        try {
            // Passo 1 - Criar envelope com dados sensíveis
            EnvelopeTCP envelope = EnvelopeTCP.builder()
                    .type(type)
                    .payload(payload)
                    .jwtToken(jwtToken)
                    .nonce(java.util.UUID.randomUUID().toString())
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Passo 2 - Cifrar envelope com AES
            SecretKey aesKey = keyManager.getPeerAESKey(peerId);
            SecretKey hmacKey = keyManager.getPeerHMACKey(peerId);
            
            AES aes = new AES(aesKey);
            HMAC hmac = new HMAC(hmacKey);

            String envelopeJson = envelope.toJson();
            String encryptedEnvelope = aes.encrypt(envelopeJson);

            // Passo 3 - Gerar HMAC e assinar ciphertext
            String hmacValue = hmac.sign(encryptedEnvelope);
            String signature = keyManager.signBase64(encryptedEnvelope.getBytes());

            // Passo 4 - Construir mensagem externa (type=null indica envelope cifrado)
            return MessageTCP.builder()
                    .type(null)
                    .senderId(entityId)
                    .encryptedPayload(encryptedEnvelope)
                    .hmac(hmacValue)
                    .signature(signature)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao construir envelope cifrado para '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public MessageTCP buildEncryptedEnvelope(String peerId, MessageTypeTCP type, String payload) {
        return buildEncryptedEnvelope(peerId, type, payload, null);
    }

    public EnvelopeTCP decryptEnvelope(String peerId, MessageTCP message) {
        try {
            AES aes = new AES(keyManager.getPeerAESKey(peerId));
            String envelopeJson = aes.decrypt(message.getEncryptedPayload());
            return EnvelopeTCP.fromJson(envelopeJson);
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao decifrar envelope de '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public boolean isEncryptedEnvelope(MessageTCP message) {
        return message.getType() == null && message.getEncryptedPayload() != null;
    }

    // ==================== HANDSHAKE ====================

    public MessageTCP buildHello() {
        return MessageTCP.builder()
                .type(MessageTypeTCP.HELLO)
                .senderId(entityId)
                .senderPublicKey(keyManager.getPublicKeyBase64())
                .build();
    }

    public MessageTCP handleHello(MessageTCP hello) {
        String peerId = hello.getSenderId();
        try {
            keyManager.storePeerKey(peerId, hello.getSenderPublicKey());
            keyManager.generateSessionKeys(peerId);
            String encryptedKeys = keyManager.encryptSessionKeysForPeer(peerId);

            return MessageTCP.builder()
                    .type(MessageTypeTCP.CHALLENGE)
                    .senderId(entityId)
                    .senderPublicKey(keyManager.getPublicKeyBase64())
                    .encryptedSessionKeys(encryptedKeys)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao processar HELLO de '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public boolean handleChallenge(MessageTCP challenge) {
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

    public String getEntityId() {
        return entityId;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public Socket getSocket() {
        return socket;
    }

    public void clearPeerSession(String peerId) {
        keyManager.clearPeerKeys(peerId);
    }

    public void close() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
            logger.debug("Canal fechado");
        } catch (IOException e) {
            logger.error("Erro ao fechar canal: {}", e.getMessage());
        }
    }
}
