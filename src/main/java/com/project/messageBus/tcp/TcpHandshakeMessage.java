package com.project.messageBus.tcp;

import com.project.messageBus.Message;
import com.project.messageBus.MessageType;
import com.project.security.CryptoProtocol;
import com.project.security.SessionKeys;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.util.Base64;

public class TcpHandshakeMessage extends Message {
    
    private static final long serialVersionUID = 1L;
    private static final String SEPARATOR = "||";
    
    private String payload;

    public TcpHandshakeMessage(MessageType tipo, String payload) {
        super(tipo);
        
        if (!tipo.isTcpHandshake()) {
            throw new IllegalArgumentException("Tipo deve ser um dos tipos de handshake TCP");
        }
        
        this.payload = payload;
    }
    
    public String getPayload() {
        return payload;
    }

    @Override
    public byte[] serialize() {
        String data = type.name() + SEPARATOR + payload;
        return data.getBytes(StandardCharsets.UTF_8);
    }

    public static TcpHandshakeMessage deserialize(byte[] data) {
        try {
            String str = new String(data, StandardCharsets.UTF_8);
            String[] parts = str.split("\\|\\|", 2);
            
            if (parts.length < 2) {
                throw new IllegalArgumentException("Formato inválido: esperado tipo||payload");
            }
            
            MessageType tipo = MessageType.valueOf(parts[0]);
            String payload = parts[1];
            
            return new TcpHandshakeMessage(tipo, payload);
            
        } catch (Exception e) {
            System.err.println("[TcpHandshakeMessage] Erro ao deserializar: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    @Override
    public String toJSON() {
        return String.format("{\"type\":\"%s\",\"payload\":\"%s\",\"timestamp\":%d}",
                type.name(), payload, timestamp);
    }
    
    @Override
    public void validate() throws IllegalStateException {
        if (!type.isTcpHandshake()) {
            throw new IllegalStateException("Tipo de mensagem inválido para TcpHandshakeMessage");
        }
        if (payload == null || payload.isEmpty()) {
            throw new IllegalStateException("Payload não pode ser nulo ou vazio");
        }
    }
    
    @Override
    public int getSize() {
        return serialize().length;
    }
    
    @Override
    public String toString() {
        return String.format("TcpHandshakeMessage{tipo=%s, payload=%s, timestamp=%d}",
                type, payload.length() > 50 ? payload.substring(0, 50) + "..." : payload, timestamp);
    }
    
    // ========== MÉTODOS FACTORY PARA CRIAR MENSAGENS ESPECÍFICAS ==========

    public static TcpHandshakeMessage createHello(String edgeId) {
        return new TcpHandshakeMessage(MessageType.TCP_HELLO, edgeId);
    }

    public static TcpHandshakeMessage createChallenge(String publicKeyBase64) {
        return new TcpHandshakeMessage(MessageType.TCP_CHALLENGE, publicKeyBase64);
    }

    public static TcpHandshakeMessage createChallenge(PublicKey publicKey) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return new TcpHandshakeMessage(MessageType.TCP_CHALLENGE, publicKeyBase64);
    }

    public static TcpHandshakeMessage createKeyExchange(SessionKeys sessionKeys, PublicKey publicKey) throws Exception {
        // Serializar SessionKeys
        String keysData = String.format("%s||%s",
                Base64.getEncoder().encodeToString(sessionKeys.getAesKey().getEncoded()),
                Base64.getEncoder().encodeToString(sessionKeys.getHmacKey().getEncoded())
        );
        
        // Cifrar com RSA
        byte[] encryptedKeys = CryptoProtocol.encryptRSA(keysData.getBytes(StandardCharsets.UTF_8), publicKey);
        String encryptedKeysBase64 = Base64.getEncoder().encodeToString(encryptedKeys);
        
        return new TcpHandshakeMessage(MessageType.TCP_KEY_EXCHANGE, encryptedKeysBase64);
    }

    public SessionKeys extractSessionKeys(PrivateKey privateKey) throws Exception {
        if (type != MessageType.TCP_KEY_EXCHANGE) {
            throw new IllegalStateException("Mensagem não é do tipo KEY_EXCHANGE");
        }
        
        // Decifrar com RSA
        byte[] encryptedKeys = Base64.getDecoder().decode(payload);
        byte[] decryptedKeys = CryptoProtocol.decryptRSA(encryptedKeys, privateKey);
        String keysData = new String(decryptedKeys, StandardCharsets.UTF_8);
        
        // Desserializar SessionKeys
        String[] parts = keysData.split("\\|\\|");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Formato de chaves inválido");
        }
        
        byte[] aesKeyBytes = Base64.getDecoder().decode(parts[0]);
        byte[] hmacKeyBytes = Base64.getDecoder().decode(parts[1]);
        
        javax.crypto.SecretKey aesKey = new javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES");
        javax.crypto.SecretKey hmacKey = new javax.crypto.spec.SecretKeySpec(hmacKeyBytes, "HmacSHA256");
        
        // Criar SessionKeys com informações básicas
        String clientId = "EDGE_CLIENT";
        long now = System.currentTimeMillis();
        long expiration = now + (24 * 60 * 60 * 1000); // 24 horas
        
        return new SessionKeys(clientId, aesKey, hmacKey, now, expiration);
    }

    public static TcpHandshakeMessage createAck() {
        return createAck("Handshake bem-sucedido");
    }

    public static TcpHandshakeMessage createAck(String message) {
        return new TcpHandshakeMessage(MessageType.TCP_ACK, message);
    }

    public static TcpHandshakeMessage createError(String errorMessage) {
        return new TcpHandshakeMessage(MessageType.TCP_ERROR, errorMessage);
    }
    
    // ========== MÉTODOS DE CONVENIÊNCIA ==========

    public boolean isHello() {
        return type == MessageType.TCP_HELLO;
    }

    public boolean isChallenge() {
        return type == MessageType.TCP_CHALLENGE;
    }

    public boolean isKeyExchange() {
        return type == MessageType.TCP_KEY_EXCHANGE;
    }

    public boolean isAck() {
        return type == MessageType.TCP_ACK;
    }

    public boolean isError() {
        return type == MessageType.TCP_ERROR;
    }

    public String getEdgeId() {
        if (!isHello()) {
            throw new IllegalStateException("Mensagem não é do tipo HELLO");
        }
        return payload;
    }

    public PublicKey getPublicKey() throws Exception {
        if (!isChallenge()) {
            throw new IllegalStateException("Mensagem não é do tipo CHALLENGE");
        }
        
        byte[] keyBytes = Base64.getDecoder().decode(payload);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new java.security.spec.X509EncodedKeySpec(keyBytes));
    }

    public String getErrorMessage() {
        if (!isError()) {
            throw new IllegalStateException("Mensagem não é do tipo ERROR");
        }
        return payload;
    }
}
