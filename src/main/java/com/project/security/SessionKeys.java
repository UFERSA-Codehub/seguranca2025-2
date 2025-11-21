package com.project.security;

import javax.crypto.SecretKey;

public class SessionKeys {
    private final String clientId;
    private final SecretKey aesKey;
    private final SecretKey hmacKey;
    private final long timestamp;
    private final long expirationTime;

    public SessionKeys(String clientId, SecretKey aesKey, SecretKey hmacKey, long timestamp, long expirationTime) {
        this.clientId = clientId;
        this.aesKey = aesKey;
        this.hmacKey = hmacKey;
        this.timestamp = timestamp;
        //this.timestamp = System.currentTimeMillis();
        this.expirationTime = expirationTime;
    }

    public String getClientId() {
        return clientId;
    }

    public SecretKey getAesKey() {
        return aesKey;
    }

    public SecretKey getHmacKey() {
        return hmacKey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }

    public long getTempoRestante() {
        long tempoRestante = expirationTime - System.currentTimeMillis();
        return tempoRestante > 0 ? tempoRestante / 1000 : 0; // Retorna em segundos
    }

    @Override
    public String toString() {
        return String.format("SessionKeys{clientId='%s', expira=%d}",
                clientId, getTempoRestante());
    }
}