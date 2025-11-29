package com.project.message.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MessageHTTP {
    private static final Gson gson = new GsonBuilder().create();

    private MessageTypeHTTP type;
    private String clientId;
    private String senderPublicKey;
    private String encryptedSessionKeys;
    private String encryptedPayload;
    private String signature;
    private String jwtToken;

    public MessageHTTP() {}

    public MessageHTTP(MessageTypeHTTP type, String clientId) {
        this.type = type;
        this.clientId = clientId;
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static MessageHTTP fromJson(String json) {
        return gson.fromJson(json, MessageHTTP.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final MessageHTTP message = new MessageHTTP();

        public Builder type(MessageTypeHTTP type) {
            message.type = type;
            return this;
        }

        public Builder clientId(String clientId) {
            message.clientId = clientId;
            return this;
        }

        public Builder senderPublicKey(String senderPublicKey) {
            message.senderPublicKey = senderPublicKey;
            return this;
        }

        public Builder encryptedSessionKeys(String encryptedSessionKeys) {
            message.encryptedSessionKeys = encryptedSessionKeys;
            return this;
        }

        public Builder encryptedPayload(String encryptedPayload) {
            message.encryptedPayload = encryptedPayload;
            return this;
        }

        public Builder signature(String signature) {
            message.signature = signature;
            return this;
        }

        public Builder jwtToken(String jwtToken) {
            message.jwtToken = jwtToken;
            return this;
        }

        public MessageHTTP build() {
            return message;
        }
    }

    // Getters e Setters
    public MessageTypeHTTP getType() { return type; }
    public void setType(MessageTypeHTTP type) { this.type = type; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getSenderPublicKey() { return senderPublicKey; }
    public void setSenderPublicKey(String senderPublicKey) { this.senderPublicKey = senderPublicKey; }

    public String getEncryptedSessionKeys() { return encryptedSessionKeys; }
    public void setEncryptedSessionKeys(String encryptedSessionKeys) { this.encryptedSessionKeys = encryptedSessionKeys; }

    public String getEncryptedPayload() { return encryptedPayload; }
    public void setEncryptedPayload(String encryptedPayload) { this.encryptedPayload = encryptedPayload; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getJwtToken() { return jwtToken; }
    public void setJwtToken(String jwtToken) { this.jwtToken = jwtToken; }

    @Override
    public String toString() {
        return "MessageHTTP{type=" + type + ", clientId='" + clientId + "'}";
    }
}
