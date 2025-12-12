package com.project.tracing;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public record TraceEvent(
    String traceId,
    long timestamp,
    long sequenceNumber,
    String componentType,
    String componentId,
    String protocol,
    String direction,
    String remoteAddress,
    String localAddress,
    String messageType,
    String encryptedPayload,
    String decryptedPayload,
    String peerId
) {
    private static final Gson gson = new GsonBuilder().create();
    
    // Contador global para ordenação de eventos com mesmo timestamp
    private static final AtomicLong SEQUENCE_COUNTER = new AtomicLong(0);

    public static TraceEvent create(
            String componentId,
            String protocol,
            String direction,
            String remoteAddress,
            String localAddress,
            String messageType,
            String encryptedPayload,
            String decryptedPayload,
            String peerId
    ) {
        return new TraceEvent(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            SEQUENCE_COUNTER.incrementAndGet(),
            extractComponentType(componentId),
            componentId,
            protocol,
            direction,
            remoteAddress,
            localAddress,
            messageType,
            encryptedPayload,
            decryptedPayload,
            peerId
        );
    }

    public String toJson() {
        return gson.toJson(this);
    }

    private static String extractComponentType(String entityId) {
        if (entityId == null) return "UNKNOWN";
        // Preserva o ID completo para clientes externos (SENSOR_001, CLIENT_002, MALICIOUS_003)
        // Isso permite que o dashboard crie nós distintos para cada sensor
        if (entityId.startsWith("SENSOR_")) return entityId;
        if (entityId.startsWith("CLIENT_")) return entityId;
        if (entityId.startsWith("MALICIOUS_")) return entityId;
        return entityId;
    }
}
