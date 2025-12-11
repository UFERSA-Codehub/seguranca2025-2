package com.project.collector;

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

    public String toJson() {
        return gson.toJson(this);
    }

    public static TraceEvent fromJson(String json) {
        return gson.fromJson(json, TraceEvent.class);
    }
}
