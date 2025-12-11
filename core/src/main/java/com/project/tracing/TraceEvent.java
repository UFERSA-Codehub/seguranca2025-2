package com.project.tracing;

import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public record TraceEvent(
    String traceId,
    long timestamp,
    String componentType,
    String componentId,
    String protocol,
    String direction,
    String remoteAddress,
    String messageType,
    String encryptedPayload,
    String decryptedPayload,
    String peerId
) {
    private static final Gson gson = new GsonBuilder().create();

    public static TraceEvent create(
            String componentId,
            String protocol,
            String direction,
            String remoteAddress,
            String messageType,
            String encryptedPayload,
            String decryptedPayload,
            String peerId
    ) {
        return new TraceEvent(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            extractComponentType(componentId),
            componentId,
            protocol,
            direction,
            remoteAddress,
            messageType,
            encryptedPayload,
            decryptedPayload,
            peerId
        );
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static TraceEvent fromJson(String json) {
        return gson.fromJson(json, TraceEvent.class);
    }

    public static String extractComponentType(String entityId) {
        if (entityId == null) return "UNKNOWN";
        if (entityId.startsWith("SENSOR")) return "SENSOR";
        if (entityId.startsWith("CLIENT")) return "CLIENT";
        if (entityId.startsWith("MALICIOUS")) return "MALICIOUS_SENSOR";
        return entityId;
    }
}
