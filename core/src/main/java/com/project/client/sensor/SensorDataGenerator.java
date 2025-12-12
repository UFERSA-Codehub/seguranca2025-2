package com.project.client.sensor;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public class SensorDataGenerator {

    private static final double SPIKE_CHANCE = 0.07;
    private static final double SPIKE_RECOVERY_RATE = 0.15;

    private final String sensorId;
    private final ThreadLocalRandom rand = ThreadLocalRandom.current();

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

    private boolean spikeActive = false;
    private String spikeMetric = null;

    public SensorDataGenerator(String sensorId) {
        this.sensorId = sensorId;
        initializeBaseValues();
    }

    private void initializeBaseValues() {
        co2 = 450 + rand.nextDouble(-50, 50);
        co = 1.0 + rand.nextDouble(-0.3, 0.3);
        no2 = 25 + rand.nextDouble(-5, 5);
        so2 = 10 + rand.nextDouble(-3, 3);
        pm25 = 18 + rand.nextDouble(-5, 5);
        pm10 = 35 + rand.nextDouble(-8, 8);
        temperature = 22 + rand.nextDouble(-3, 3);
        humidity = 55 + rand.nextDouble(-10, 10);
        noiseLevel = 50 + rand.nextDouble(-8, 8);
        uvIndex = 4 + rand.nextDouble(-1, 1);
    }

    public SensorData generate() {
        if (spikeActive) {
            recoverFromSpike();
        } else if (rand.nextDouble() < SPIKE_CHANCE) {
            triggerSpike();
        } else {
            applyGradualDrift();
        }

        return buildSensorData();
    }

    private void applyGradualDrift() {
        co2 = drift(co2, 0.02, 350, 600);
        co = drift(co, 0.03, 0.2, 3);
        no2 = drift(no2, 0.025, 10, 50);
        so2 = drift(so2, 0.025, 2, 25);
        pm25 = drift(pm25, 0.03, 5, 35);
        pm10 = drift(pm10, 0.03, 15, 70);
        temperature = drift(temperature, 0.01, 15, 32);
        humidity = drift(humidity, 0.02, 35, 75);
        noiseLevel = drift(noiseLevel, 0.05, 35, 70);
        uvIndex = drift(uvIndex, 0.02, 1, 7);
    }

    private double drift(double current, double driftRate, double min, double max) {
        double change = current * driftRate * rand.nextDouble(-1, 1);
        double newValue = current + change;
        return clamp(newValue, min, max);
    }

    private void triggerSpike() {
        spikeActive = true;
        int choice = rand.nextInt(4);
        switch (choice) {
            case 0 -> {
                spikeMetric = "humidity";
                humidity = rand.nextDouble(96, 100);
            }
            case 1 -> {
                spikeMetric = "uvIndex";
                uvIndex = rand.nextDouble(10.5, 11.5);
            }
            case 2 -> {
                spikeMetric = "pm25";
                pm25 = rand.nextDouble(55, 100);
            }
            case 3 -> {
                spikeMetric = "noiseLevel";
                noiseLevel = rand.nextDouble(102, 115);
            }
        }
    }

    private void recoverFromSpike() {
        switch (spikeMetric) {
            case "humidity" -> {
                humidity = recover(humidity, 55, SPIKE_RECOVERY_RATE, 35, 75);
                if (humidity < 90) spikeActive = false;
            }
            case "uvIndex" -> {
                uvIndex = recover(uvIndex, 4, SPIKE_RECOVERY_RATE, 1, 7);
                if (uvIndex < 9) spikeActive = false;
            }
            case "pm25" -> {
                pm25 = recover(pm25, 18, SPIKE_RECOVERY_RATE, 5, 35);
                if (pm25 < 45) spikeActive = false;
            }
            case "noiseLevel" -> {
                noiseLevel = recover(noiseLevel, 50, SPIKE_RECOVERY_RATE, 35, 70);
                if (noiseLevel < 95) spikeActive = false;
            }
        }
        applyGradualDriftExcept(spikeMetric);
    }

    private void applyGradualDriftExcept(String except) {
        if (!"co2".equals(except)) co2 = drift(co2, 0.02, 350, 600);
        if (!"co".equals(except)) co = drift(co, 0.03, 0.2, 3);
        if (!"no2".equals(except)) no2 = drift(no2, 0.025, 10, 50);
        if (!"so2".equals(except)) so2 = drift(so2, 0.025, 2, 25);
        if (!"pm25".equals(except)) pm25 = drift(pm25, 0.03, 5, 35);
        if (!"pm10".equals(except)) pm10 = drift(pm10, 0.03, 15, 70);
        if (!"temperature".equals(except)) temperature = drift(temperature, 0.01, 15, 32);
        if (!"humidity".equals(except)) humidity = drift(humidity, 0.02, 35, 75);
        if (!"noiseLevel".equals(except)) noiseLevel = drift(noiseLevel, 0.05, 35, 70);
        if (!"uvIndex".equals(except)) uvIndex = drift(uvIndex, 0.02, 1, 7);
    }

    private double recover(double current, double target, double rate, double min, double max) {
        double step = (target - current) * rate;
        double noise = current * 0.01 * rand.nextDouble(-1, 1);
        return clamp(current + step + noise, min, max);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private SensorData buildSensorData() {
        return new SensorData(
            sensorId,
            Instant.now().toString(),
            co2, co, no2, so2,
            pm25, pm10,
            temperature, humidity,
            noiseLevel, uvIndex
        );
    }
}
