package com.project.server.datacenter.db;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

public class DataStore {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter.DataStore");
    
    private final List<SensorReading> readings;

    public DataStore() {
        this.readings = new CopyOnWriteArrayList<>();
    }

    public void store(SensorReading reading) {
        readings.add(reading);
        logger.debug("Leitura armazenada: sensor={}, alert={}", reading.sensorId(), reading.isAlert());
    }

    public void store(String sensorId, long timestamp, JsonObject data, boolean isAlert, String alertType) {
        store(new SensorReading(sensorId, timestamp, data, isAlert, alertType));
    }

    public List<SensorReading> getAll() {
        return new ArrayList<>(readings);
    }

    public List<SensorReading> getAll(int offset, int limit) {
        int size = readings.size();
        if (offset >= size) {
            return new ArrayList<>();
        }
        // Retorna em ordem reversa (mais recentes primeiro)
        List<SensorReading> reversed = new ArrayList<>(readings);
        java.util.Collections.reverse(reversed);
        int end = Math.min(offset + limit, size);
        return reversed.subList(offset, end);
    }

    public List<SensorReading> getAlerts() {
        return readings.stream()
                .filter(SensorReading::isAlert)
                .collect(Collectors.toList());
    }

    public List<SensorReading> getAlerts(int offset, int limit) {
        List<SensorReading> alerts = readings.stream()
                .filter(SensorReading::isAlert)
                .collect(Collectors.toList());
        int size = alerts.size();
        if (offset >= size) {
            return new ArrayList<>();
        }
        java.util.Collections.reverse(alerts);
        int end = Math.min(offset + limit, size);
        return alerts.subList(offset, end);
    }

    public int getCount() {
        return readings.size();
    }

    public int getAlertCount() {
        return (int) readings.stream().filter(SensorReading::isAlert).count();
    }

    public List<String> getSensorIds() {
        return readings.stream()
                .map(SensorReading::sensorId)
                .distinct()
                .collect(Collectors.toList());
    }

    public void clear() {
        readings.clear();
        logger.info("DataStore limpo");
    }

    public record SensorReading(
        String sensorId,
        long timestamp,
        JsonObject data,
        boolean isAlert,
        String alertType
    ) {}
}
