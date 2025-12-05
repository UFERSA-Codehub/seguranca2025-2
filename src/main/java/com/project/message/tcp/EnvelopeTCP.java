package com.project.message.tcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class EnvelopeTCP {
    private static final Gson gson = new GsonBuilder().create();

    private MessageTypeTCP type;
    private String jwtToken;
    private String payload;
    private String nonce;
    private long timestamp;

    public EnvelopeTCP() {}

    public String toJson() {
        return gson.toJson(this);
    }

    public static EnvelopeTCP fromJson(String json) {
        return gson.fromJson(json, EnvelopeTCP.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final EnvelopeTCP envelope = new EnvelopeTCP();

        public Builder type(MessageTypeTCP type) {
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

        public EnvelopeTCP build() {
            return envelope;
        }
    }

    public MessageTypeTCP getType() { return type; }
    public void setType(MessageTypeTCP type) { this.type = type; }

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
        return "EnvelopeTCP{type=" + type + ", nonce='" + nonce + "'}";
    }
}
