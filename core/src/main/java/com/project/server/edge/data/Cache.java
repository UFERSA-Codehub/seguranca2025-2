package com.project.server.edge.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

public class Cache {
    private static final Logger logger = LoggerFactory.getLogger("Edge.Cache");
    private static final int MAX_ENTRIES_PER_SENSOR = 1000;
    
    // sensorId -> lista de entradas de dados
    private final Map<String, List<CacheEntry>> sensorData;
    private int totalCount;

    public Cache() {
        this.sensorData = new ConcurrentHashMap<>();
        this.totalCount = 0;
    }

    public synchronized void store(String sensorId, JsonObject data) {
        store(sensorId, data, false, null);
    }

    public synchronized void store(String sensorId, JsonObject data, boolean isAlert, String alertType) {
        List<CacheEntry> entries = sensorData.computeIfAbsent(sensorId, k -> new ArrayList<>());
        
        // Caso limite atingido, remove entrada mais antiga
        if (entries.size() >= MAX_ENTRIES_PER_SENSOR) {
            entries.remove(0);
            totalCount--;
        }

        entries.add(new CacheEntry(sensorId, System.currentTimeMillis(), data, isAlert, alertType));
        totalCount++;
        
        logger.debug("Dados armazenados para sensor '{}': {} entradas, alert={}", sensorId, entries.size(), isAlert);
    }

    public List<CacheEntry> getDataForSensor(String sensorId) {
        return sensorData.getOrDefault(sensorId, new ArrayList<>());
    }

    public CacheEntry getLatestForSensor(String sensorId) {
        List<CacheEntry> entries = sensorData.get(sensorId);
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        return entries.get(entries.size() - 1);
    }

    public Map<String, List<CacheEntry>> getAllData() {
        return new ConcurrentHashMap<>(sensorData);
    }

    public List<CacheEntry> getAllEntries() {
        List<CacheEntry> all = new ArrayList<>();
        for (List<CacheEntry> entries : sensorData.values()) {
            all.addAll(entries);
        }
        return all;
    }

    public int getCount() {
        return totalCount;
    }

    public int getSensorCount() {
        return sensorData.size();
    }

    public synchronized void clear() {
        sensorData.clear();
        totalCount = 0;
        logger.info("Cache limpo");
    }

    public synchronized List<CacheEntry> flush() {
        List<CacheEntry> flushedData = getAllEntries();
        clear();
        return flushedData;
    }

    public record CacheEntry(
        String sensorId,
        long timestamp, 
        JsonObject data,
        boolean isAlert,
        String alertType
    ) {
        public JsonObject toJson() {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("sensorId", sensorId);
            wrapper.addProperty("timestamp", timestamp);
            wrapper.add("data", data);
            wrapper.addProperty("isAlert", isAlert);
            if (alertType != null) {
                wrapper.addProperty("alertType", alertType);
            }
            return wrapper;
        }
    }
}
