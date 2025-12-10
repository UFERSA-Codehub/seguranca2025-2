package com.project.server.edge.data;

import com.google.gson.JsonObject;

public class AlertDetector {

    private AlertDetector() {
        // Classe utilitária - não instanciar
    }

    // Detecta alertas baseado em thresholds críticos (intervals.md)
    // - humidity > 95% → flood
    // - uvIndex > 10 → uv
    // - pm25 > 50 µg/m³ → pollution
    // - noiseLevel > 100 dB → noise
    public static String detectAlert(JsonObject data) {
        // Passo 1 - Verificar umidade alta (risco de enchente)
        if (data.has("humidity")) {
            double humidity = data.get("humidity").getAsDouble();
            if (humidity > 95) {
                return "flood";
            }
        }

        // Passo 2 - Verificar índice UV extremo
        if (data.has("uvIndex")) {
            double uv = data.get("uvIndex").getAsDouble();
            if (uv > 10) {
                return "uv";
            }
        }

        // Passo 3 - Verificar poluição crítica (PM2.5)
        if (data.has("pm25")) {
            double pm25 = data.get("pm25").getAsDouble();
            if (pm25 > 50) {
                return "pollution";
            }
        }

        // Passo 4 - Verificar ruído excessivo
        if (data.has("noiseLevel")) {
            double noise = data.get("noiseLevel").getAsDouble();
            if (noise > 100) {
                return "noise";
            }
        }

        return null;
    }

    public static boolean detectAnomaly(JsonObject data) {
        // Passo 1 - Extrair valores dos sensores (com valores padrão seguros)
        double temp = data.has("temperatura") ? data.get("temperatura").getAsDouble() : 0;
        double humidity = data.has("umidade") ? data.get("umidade").getAsDouble() : 50;
        double co2 = data.has("co2") ? data.get("co2").getAsDouble() : 400;
        double pm25 = data.has("pm25") ? data.get("pm25").getAsDouble() : 10;
        double noise = data.has("ruido") ? data.get("ruido").getAsDouble() : 50;

        // Passo 2 - Verificar se valores estão fora das faixas aceitáveis
        if (temp < -40 || temp > 60) return true;
        if (humidity < 0 || humidity > 100) return true;
        if (co2 < 200 || co2 > 5000) return true;
        if (pm25 < 0 || pm25 > 500) return true;
        if (noise < 0 || noise > 150) return true;

        return false;
    }
}
