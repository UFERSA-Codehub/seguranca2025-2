package com.project.server.ids;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.project.server.ids.AlertStore.Alert;

public class ReportGenerator {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AlertStore alertStore;

    public ReportGenerator(AlertStore alertStore) {
        this.alertStore = alertStore;
    }

    public String generateFullReport() {
        JsonObject report = new JsonObject();
        report.addProperty("reportType", "FULL_IDS_REPORT");
        report.addProperty("generatedAt", formatter.format(Instant.now()));
        report.addProperty("totalAlerts", alertStore.getTotalCount());

        // Estatísticas por tipo de alerta
        JsonObject statsByType = new JsonObject();
        Map<String, Long> countByType = alertStore.getAlertCountByType();
        countByType.forEach((type, count) -> statsByType.addProperty(type, count));
        report.add("alertsByType", statsByType);

        // IPs distintos
        List<String> distinctIps = alertStore.getDistinctIps();
        report.addProperty("distinctSourceIps", distinctIps.size());

        // Lista de alertas
        JsonArray alertsArray = new JsonArray();
        for (Alert alert : alertStore.getAll()) {
            alertsArray.add(alertToJson(alert));
        }
        report.add("alerts", alertsArray);

        return gson.toJson(report);
    }

    public String generateReportByIp(String ip) {
        JsonObject report = new JsonObject();
        report.addProperty("reportType", "IP_SPECIFIC_REPORT");
        report.addProperty("generatedAt", formatter.format(Instant.now()));
        report.addProperty("sourceIp", ip);

        List<Alert> alerts = alertStore.getByIp(ip);
        report.addProperty("totalAlerts", alerts.size());

        JsonArray alertsArray = new JsonArray();
        for (Alert alert : alerts) {
            alertsArray.add(alertToJson(alert));
        }
        report.add("alerts", alertsArray);

        return gson.toJson(report);
    }

    public String generateSummaryReport() {
        JsonObject report = new JsonObject();
        report.addProperty("reportType", "SUMMARY_REPORT");
        report.addProperty("generatedAt", formatter.format(Instant.now()));
        report.addProperty("totalAlerts", alertStore.getTotalCount());

        // Estatísticas por tipo
        JsonObject statsByType = new JsonObject();
        Map<String, Long> countByType = alertStore.getAlertCountByType();
        countByType.forEach((type, count) -> statsByType.addProperty(type, count));
        report.add("alertsByType", statsByType);

        // Top IPs ofensores
        JsonArray topIps = new JsonArray();
        List<String> ips = alertStore.getDistinctIps();
        ips.stream()
            .sorted((a, b) -> Integer.compare(
                alertStore.getByIp(b).size(), 
                alertStore.getByIp(a).size()))
            .limit(10)
            .forEach(ip -> {
                JsonObject ipInfo = new JsonObject();
                ipInfo.addProperty("ip", ip);
                ipInfo.addProperty("alertCount", alertStore.getByIp(ip).size());
                topIps.add(ipInfo);
            });
        report.add("topOffendingIps", topIps);

        return gson.toJson(report);
    }

    private JsonObject alertToJson(Alert alert) {
        JsonObject obj = new JsonObject();
        obj.addProperty("sourceIp", alert.sourceIp());
        obj.addProperty("sourcePort", alert.sourcePort());
        obj.addProperty("destService", alert.destService());
        obj.addProperty("alertType", alert.alertType());
        obj.addProperty("content", alert.content());
        obj.addProperty("timestamp", formatter.format(Instant.ofEpochMilli(alert.timestamp())));
        return obj;
    }
}
