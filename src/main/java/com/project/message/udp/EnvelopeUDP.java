package com.project.message.udp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class EnvelopeUDP {
    private static final Gson gson = new GsonBuilder().create();

    private MessageTypeUDP type;
    private String jwtToken;
    private String payload;
    private String nonce;
    private long timestamp;

    public EnvelopeUDP() {}

    public String toJson() {
        return gson.toJson(this);
    }

    public static EnvelopeUDP fromJson(String json) {
        return gson.fromJson(json, EnvelopeUDP.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final EnvelopeUDP envelope = new EnvelopeUDP();

        public Builder type(MessageTypeUDP type) {
            envelope.type = type;
            return this;
        }

        public Builder jwtToken(String jwtToken) {
            envelope.jwtToken = jwtToken;
            return this;
        }

        public Builder payload(String payload) {
            envelope.payload = payload;
            return this;
        }

        public Builder nonce(String nonce) {
            envelope.nonce = nonce;
            return this;
        }

        public Builder timestamp(long timestamp) {
            envelope.timestamp = timestamp;
            return this;
        }

        public EnvelopeUDP build() {
            return envelope;
        }
    }

    public MessageTypeUDP getType() { return type; }
    public void setType(MessageTypeUDP type) { this.type = type; }

    public String getJwtToken() { return jwtToken; }
    public void setJwtToken(String jwtToken) { this.jwtToken = jwtToken; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "EnvelopeUDP{type=" + type + ", nonce='" + nonce + "'}";
    }
}
