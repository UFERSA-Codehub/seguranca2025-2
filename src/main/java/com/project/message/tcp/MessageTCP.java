package com.project.message.tcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MessageTCP {
    private static final Gson gson = new GsonBuilder().create();

    private MessageTypeTCP type;                    // Tipo da mensagem de acordo com MessageTypeTCP.java
    private String senderId;                        // Identificador único do emissor, ex: UUID | Nesse caso simplificado como SENSOR_001, EDGE_001, etc.
    private String senderPublicKey;                 // Base64 (HELLO/CHALLENGE)
    private String encryptedSessionKeys;            // Base64 RSA (CHALLENGE)
    private String encryptedPayload;                // Base64 (AES) (DATA)
    private String signature;                       // Base64 assinatura RSA do ciphertext
    private String jwtToken;                        // JWT Token para autenticação/validação (OPCIONAL, para Sensor -> Edge)

    public MessageTCP() {

    }

    public MessageTCP(MessageTypeTCP type, String senderId){
        this.type = type;
        this.senderId = senderId;
    }

    public String toJson(){
        return gson.toJson(this);
    }

    public static MessageTCP fromJson(String json){
        return gson.fromJson(json, MessageTCP.class);
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder {
        private final MessageTCP message = new MessageTCP();

        public Builder type(MessageTypeTCP type){
            message.type = type;
            return this;
        }

        public Builder senderId(String senderId){
            message.senderId = senderId;
            return this;
        }

        public Builder senderPublicKey(String senderPublicKey){
            message.senderPublicKey = senderPublicKey;
            return this;
        }

        public Builder encryptedSessionKeys(String encryptedSessionKeys){
            message.encryptedSessionKeys = encryptedSessionKeys;
            return this;
        }

        public Builder encryptedPayload(String encryptedPayload){
            message.encryptedPayload = encryptedPayload;
            return this;
        }

        public Builder signature(String signature){
            message.signature = signature;
            return this;
        }

        public Builder jwtToken(String jwtToken){
            message.jwtToken = jwtToken;
            return this;
        }

        public MessageTCP build(){
            return message;
        }
    }

    public MessageTypeTCP getType() {
        return type;
    }

    public void setType(MessageTypeTCP type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public void setSenderPublicKey(String senderPublicKey) {
        this.senderPublicKey = senderPublicKey;
    }

    public String getEncryptedSessionKeys() {
        return encryptedSessionKeys;
    }

    public void setEncryptedSessionKeys(String encryptedSessionKeys) {
        this.encryptedSessionKeys = encryptedSessionKeys;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public void setEncryptedPayload(String encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    public boolean isHandshake() {
        return type == MessageTypeTCP.HELLO || type == MessageTypeTCP.CHALLENGE;
    }

    @Override
    public String toString() {
        return "MessageTCP{type=" + type + ", senderId='" + senderId + '\'' + '}';
    }

}
