package com.project.client.sensor;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SensorData {
    private static final Gson gson = new GsonBuilder().create();

    private String sensorId;
    private String timestamp;

    private double co2;
    private double co;
    private double no2;
    private double so2;

    private double pm25;
    private double pm10;

    private double temperature;
    private double humidity;

    private double noiseLevel;
    private double uvIndex;

    public SensorData() {}

    public static SensorData generateRandom(String sensorId) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        
        SensorData data = new SensorData();
        data.sensorId = sensorId;
        data.timestamp = Instant.now().toString();

        data.co2 = rand.nextDouble(350, 1000);
        data.co = rand.nextDouble(0, 10);
        data.no2 = rand.nextDouble(0, 200);
        data.so2 = rand.nextDouble(0, 100);
        data.pm25 = rand.nextDouble(0, 150);
        data.pm10 = rand.nextDouble(0, 300);
        data.temperature = rand.nextDouble(-10, 45);
        data.humidity = rand.nextDouble(20, 100);
        data.noiseLevel = rand.nextDouble(30, 120);
        data.uvIndex = rand.nextDouble(0, 11);
        
        return data;
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static SensorData fromJson(String json) {
        return gson.fromJson(json, SensorData.class);
    }

    public String getSensorId() { return sensorId; }
    public String getTimestamp() { return timestamp; }
    public double getCo2() { return co2; }
    public double getCo() { return co; }
    public double getNo2() { return no2; }
    public double getSo2() { return so2; }
    public double getPm25() { return pm25; }
    public double getPm10() { return pm10; }
    public double getTemperature() { return temperature; }
    public double getHumidity() { return humidity; }
    public double getNoiseLevel() { return noiseLevel; }
    public double getUvIndex() { return uvIndex; }

    @Override
    public String toString() {
        return String.format("SensorData{id=%s, temp=%.1f°C, humidity=%.1f%%, co2=%.1fppm, pm25=%.1fµg/m³}",
                sensorId, temperature, humidity, co2, pm25);
    }
}
