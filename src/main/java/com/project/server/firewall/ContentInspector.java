package com.project.server.firewall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import com.project.client.sensor.SensorData;

public class ContentInspector {
    private static final Logger logger = LoggerFactory.getLogger("ContentInspector");
    private static final Gson gson = new Gson();

    private ContentInspector() {}

    public static InspectionResult inspect(String content, String clientIp) {
        if (content == null || content.isEmpty()) {
            return InspectionResult.ok();
        }

        try {
            // Tentar parsear como SensorData
            if (content.contains("sensorId") && content.contains("temperature")) {
                return inspectSensorData(content, clientIp);
            }

            // Verificar se é JSON valido
            JsonObject json = gson.fromJson(content, JsonObject.class);
            if (json == null) {
                return InspectionResult.anomaly("MALFORMED", "Conteudo nao e JSON valido");
            }

        } catch (JsonSyntaxException e) {
            logger.debug("Conteudo nao e JSON: {}", e.getMessage());
        }

        return InspectionResult.ok();
    }

    private static InspectionResult inspectSensorData(String content, String clientIp) {
        try {
            SensorData data = SensorData.fromJson(content);
            
            // Verificar ranges (mesmos do AlertDetector)
            if (data.getTemperature() < -40 || data.getTemperature() > 60) {
                logger.warn("Temperatura anomala de {}: {}", clientIp, data.getTemperature());
                return InspectionResult.anomaly("ANOMALY", 
                    String.format("Temperatura fora do range: %.2f", data.getTemperature()));
            }

            if (data.getHumidity() < 0 || data.getHumidity() > 100) {
                logger.warn("Umidade anomala de {}: {}", clientIp, data.getHumidity());
                return InspectionResult.anomaly("ANOMALY", 
                    String.format("Umidade fora do range: %.2f", data.getHumidity()));
            }

            if (data.getCo2() < 200 || data.getCo2() > 5000) {
                logger.warn("CO2 anomalo de {}: {}", clientIp, data.getCo2());
                return InspectionResult.anomaly("ANOMALY", 
                    String.format("CO2 fora do range: %.2f", data.getCo2()));
            }

            if (data.getPm25() < 0 || data.getPm25() > 500) {
                logger.warn("PM2.5 anomalo de {}: {}", clientIp, data.getPm25());
                return InspectionResult.anomaly("ANOMALY", 
                    String.format("PM2.5 fora do range: %.2f", data.getPm25()));
            }

            if (data.getNoiseLevel() < 0 || data.getNoiseLevel() > 150) {
                logger.warn("Ruido anomalo de {}: {}", clientIp, data.getNoiseLevel());
                return InspectionResult.anomaly("ANOMALY", 
                    String.format("Ruido fora do range: %.2f", data.getNoiseLevel()));
            }

            return InspectionResult.ok();

        } catch (Exception e) {
            logger.warn("Erro ao parsear SensorData de {}: {}", clientIp, e.getMessage());
            return InspectionResult.anomaly("MALFORMED", "SensorData invalido: " + e.getMessage());
        }
    }

    public static InspectionResult inspectHttp(String httpContent, String clientIp) {
        if (httpContent == null || httpContent.isEmpty()) {
            return InspectionResult.ok();
        }

        // Extrair body do HTTP (apos headers)
        int bodyStart = httpContent.indexOf("\r\n\r\n");
        if (bodyStart == -1) {
            bodyStart = httpContent.indexOf("\n\n");
        }

        if (bodyStart != -1 && bodyStart + 4 < httpContent.length()) {
            String body = httpContent.substring(bodyStart + 4).trim();
            if (!body.isEmpty()) {
                return inspect(body, clientIp);
            }
        }

        return InspectionResult.ok();
    }

    public record InspectionResult(boolean valid, String alertType, String reason) {
        public static InspectionResult ok() {
            return new InspectionResult(true, null, null);
        }

        public static InspectionResult anomaly(String alertType, String reason) {
            return new InspectionResult(false, alertType, reason);
        }
    }
}
