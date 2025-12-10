package com.project.server.datacenter.db;

import java.io.InputStreamReader;
import java.io.StringWriter;

import java.time.Instant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import com.google.gson.JsonObject;
import com.project.server.datacenter.db.DataStore.SensorReading;

public class ReportService {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter.ReportService");
    
    private final MustacheFactory mf;
    private final Map<String, Mustache> templates;

    public ReportService() {
        this.mf = new DefaultMustacheFactory();
        this.templates = new HashMap<>();
        loadTemplates();
    }

    private void loadTemplates() {
        String[] templateNames = {"data-table", "report-air-quality", "report-flood", 
                                   "report-pollution", "report-noise", "report-uv"};
        
        for (String name : templateNames) {
            try {
                var stream = getClass().getResourceAsStream("/templates/" + name + ".html");
                if (stream != null) {
                    Mustache mustache = mf.compile(new InputStreamReader(stream), name);
                    templates.put(name, mustache);
                    logger.debug("Template carregado: {}", name);
                }
            } catch (Exception e) {
                logger.warn("Falha ao carregar template {}: {}", name, e.getMessage());
            }
        }
    }

    public String generateDataTable(List<SensorReading> readings) {
        return generateDataTable(readings, 1, 1, readings.size(), readings.size(), null);
    }

    public String generateDataTable(List<SensorReading> readings, int page, int totalPages, 
                                     int limit, int totalCount, String baseUrl) {
        Mustache template = templates.get("data-table");
        if (template == null) {
            return generateFallbackDataTable(readings);
        }

        Map<String, Object> context = new HashMap<>();
        context.put("generatedAt", Instant.now().toString());
        context.put("count", totalCount);
        context.put("pageCount", readings.size());
        context.put("readings", buildReadingsList(readings));
        
        // Paginação
        context.put("page", page);
        context.put("totalPages", totalPages);
        context.put("limit", limit);
        context.put("hasPrev", page > 1);
        context.put("hasNext", page < totalPages);
        context.put("prevPage", page - 1);
        context.put("nextPage", page + 1);
        context.put("baseUrl", baseUrl);
        context.put("showPagination", totalPages > 1);

        return render(template, context);
    }

    public String generateReport(String type, List<SensorReading> readings) {
        return switch (type) {
            case "air-quality" -> generateAirQualityReport(readings);
            case "flood" -> generateFloodReport(readings);
            case "pollution" -> generatePollutionReport(readings);
            case "noise" -> generateNoiseReport(readings);
            case "uv" -> generateUVReport(readings);
            default -> "<html><body><h1>Relatório não encontrado: " + type + "</h1></body></html>";
        };
    }

    public JsonObject generateReportJson(String type, List<SensorReading> readings) {
        return switch (type) {
            case "air-quality" -> generateAirQualityReportJson(readings);
            case "flood" -> generateFloodReportJson(readings);
            case "pollution" -> generatePollutionReportJson(readings);
            case "noise" -> generateNoiseReportJson(readings);
            case "uv" -> generateUVReportJson(readings);
            default -> {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Relatório não encontrado: " + type);
                yield error;
            }
        };
    }

    // ==================== JSON REPORTS (CLI) ====================

    private JsonObject generateAirQualityReportJson(List<SensorReading> readings) {
        double avgPm25 = average(readings, "pm25");
        double avgPm10 = average(readings, "pm10");
        double avgCo = average(readings, "co");
        double avgNo2 = average(readings, "no2");
        double avgSo2 = average(readings, "so2");

        int iqaPm25 = calculateIqaPm25(avgPm25);
        int iqaPm10 = calculateIqaPm10(avgPm10);
        int iqaCo = calculateIqaCo(avgCo);
        int iqaNo2 = calculateIqaNo2(avgNo2);
        int iqaSo2 = calculateIqaSo2(avgSo2);

        int worstIqa = Math.max(Math.max(Math.max(iqaPm25, iqaPm10), Math.max(iqaCo, iqaNo2)), iqaSo2);

        JsonObject json = new JsonObject();
        json.addProperty("type", "air-quality");
        json.addProperty("generatedAt", Instant.now().toString());
        json.addProperty("totalReadings", readings.size());
        json.addProperty("totalSensors", readings.stream().map(SensorReading::sensorId).distinct().count());
        json.addProperty("iqaValue", worstIqa);
        json.addProperty("iqaClassification", getIqaClassification(worstIqa));
        json.addProperty("mainPollutant", getMainPollutant(iqaPm25, iqaPm10, iqaCo, iqaNo2, iqaSo2));
        json.addProperty("recommendation", getIqaRecommendation(worstIqa));
        json.addProperty("avgPm25", String.format("%.1f", avgPm25));
        json.addProperty("avgPm10", String.format("%.1f", avgPm10));
        json.addProperty("avgCo", String.format("%.2f", avgCo));
        json.addProperty("avgNo2", String.format("%.1f", avgNo2));
        json.addProperty("avgSo2", String.format("%.1f", avgSo2));
        return json;
    }

    private JsonObject generateFloodReportJson(List<SensorReading> readings) {
        double avgHumidity = average(readings, "humidity");
        double avgTemperature = average(readings, "temperature");
        String alertLevel = avgHumidity > 95 ? "CRITICO" : avgHumidity > 85 ? "ATENCAO" : "NORMAL";
        String recommendation = avgHumidity > 95 
            ? "Risco elevado de alagamentos. Evite areas baixas e esteja preparado para evacuacao."
            : avgHumidity > 85 
                ? "Monitore as condicoes climaticas. Evite estacionar em areas sujeitas a alagamentos."
                : "Condicoes normais. Nenhuma acao necessaria.";

        JsonObject json = new JsonObject();
        json.addProperty("type", "flood");
        json.addProperty("generatedAt", Instant.now().toString());
        json.addProperty("totalReadings", readings.size());
        json.addProperty("totalSensors", readings.stream().map(SensorReading::sensorId).distinct().count());
        json.addProperty("avgHumidity", String.format("%.1f", avgHumidity));
        json.addProperty("avgTemperature", String.format("%.1f", avgTemperature));
        json.addProperty("alertLevel", alertLevel);
        json.addProperty("recommendation", recommendation);
        return json;
    }

    private JsonObject generatePollutionReportJson(List<SensorReading> readings) {
        double avgCo2 = average(readings, "co2");
        double avgNo2 = average(readings, "no2");
        double avgPm25 = average(readings, "pm25");
        String co2Status = avgCo2 > 1000 ? "RUIM" : avgCo2 > 450 ? "ACEITAVEL" : "EXCELENTE";
        String recommendation = avgCo2 > 1000 
            ? "Ar viciado. Abra janelas ou aumente a ventilacao do ambiente imediatamente."
            : avgCo2 > 450 
                ? "Niveis aceitaveis. Mantenha boa ventilacao para evitar acumulo."
                : "Qualidade excelente. Ambiente bem ventilado.";

        JsonObject json = new JsonObject();
        json.addProperty("type", "pollution");
        json.addProperty("generatedAt", Instant.now().toString());
        json.addProperty("totalReadings", readings.size());
        json.addProperty("totalSensors", readings.stream().map(SensorReading::sensorId).distinct().count());
        json.addProperty("avgCo2", String.format("%.1f", avgCo2));
        json.addProperty("avgNo2", String.format("%.1f", avgNo2));
        json.addProperty("avgPm25", String.format("%.1f", avgPm25));
        json.addProperty("co2Status", co2Status);
        json.addProperty("recommendation", recommendation);
        return json;
    }

    private JsonObject generateNoiseReportJson(List<SensorReading> readings) {
        double avgNoise = average(readings, "noiseLevel");
        String noiseLevel = avgNoise > 70 ? "ALTO" : avgNoise > 50 ? "MODERADO" : "BAIXO";
        String recommendation = avgNoise > 70 
            ? "Nivel de ruido elevado. Use protecao auditiva em exposicoes prolongadas."
            : avgNoise > 50 
                ? "Nivel moderado. Ambiente adequado para trabalho, mas evite exposicao continua."
                : "Ambiente silencioso. Condicoes ideais para concentracao e descanso.";

        JsonObject json = new JsonObject();
        json.addProperty("type", "noise");
        json.addProperty("generatedAt", Instant.now().toString());
        json.addProperty("totalReadings", readings.size());
        json.addProperty("totalSensors", readings.stream().map(SensorReading::sensorId).distinct().count());
        json.addProperty("avgNoise", String.format("%.1f", avgNoise));
        json.addProperty("noiseLevel", noiseLevel);
        json.addProperty("recommendation", recommendation);
        return json;
    }

    private JsonObject generateUVReportJson(List<SensorReading> readings) {
        double avgUv = average(readings, "uvIndex");
        String uvLevel = avgUv >= 11 ? "EXTREMO" : avgUv >= 8 ? "MUITO ALTO" : 
                         avgUv >= 6 ? "ALTO" : avgUv >= 3 ? "MODERADO" : "BAIXO";
        String recommendation = avgUv >= 6 
            ? "Use protetor solar e evite exposicao prolongada" 
            : "Exposicao segura com precaucoes basicas";

        JsonObject json = new JsonObject();
        json.addProperty("type", "uv");
        json.addProperty("generatedAt", Instant.now().toString());
        json.addProperty("totalReadings", readings.size());
        json.addProperty("totalSensors", readings.stream().map(SensorReading::sensorId).distinct().count());
        json.addProperty("avgUv", String.format("%.1f", avgUv));
        json.addProperty("uvLevel", uvLevel);
        json.addProperty("recommendation", recommendation);
        return json;
    }

    public List<String> getAvailableReports() {
        return List.of("air-quality", "flood", "pollution", "noise", "uv");
    }

    // ==================== RELATÓRIOS ====================

    private String generateAirQualityReport(List<SensorReading> readings) {
        Mustache template = templates.get("report-air-quality");
        
        // Calcular IQA baseado no pior poluente
        double avgPm25 = average(readings, "pm25");
        double avgPm10 = average(readings, "pm10");
        double avgCo = average(readings, "co");
        double avgNo2 = average(readings, "no2");
        double avgSo2 = average(readings, "so2");

        int iqaPm25 = calculateIqaPm25(avgPm25);
        int iqaPm10 = calculateIqaPm10(avgPm10);
        int iqaCo = calculateIqaCo(avgCo);
        int iqaNo2 = calculateIqaNo2(avgNo2);
        int iqaSo2 = calculateIqaSo2(avgSo2);

        int worstIqa = Math.max(Math.max(Math.max(iqaPm25, iqaPm10), Math.max(iqaCo, iqaNo2)), iqaSo2);
        String mainPollutant = getMainPollutant(iqaPm25, iqaPm10, iqaCo, iqaNo2, iqaSo2);
        String classification = getIqaClassification(worstIqa);
        String iqaClass = getIqaCssClass(worstIqa);
        String recommendation = getIqaRecommendation(worstIqa);

        Map<String, Object> context = new HashMap<>();
        context.put("generatedAt", Instant.now().toString());
        context.put("iqaValue", worstIqa);
        context.put("iqaClassification", classification);
        context.put("iqaClass", iqaClass);
        context.put("mainPollutant", mainPollutant);
        context.put("recommendation", recommendation);
        context.put("sensors", buildSensorPollutantList(readings));

        if (template != null) {
            return render(template, context);
        }
        return generateFallbackReport("Qualidade do Ar", context);
    }

    private String generateFloodReport(List<SensorReading> readings) {
        Mustache template = templates.get("report-flood");
        
        double avgHumidity = average(readings, "humidity");
        String alertLevel = avgHumidity > 95 ? "CRÍTICO" : avgHumidity > 85 ? "ATENÇÃO" : "NORMAL";
        String alertClass = avgHumidity > 95 ? "critico" : avgHumidity > 85 ? "atencao" : "normal";
        String recommendation = avgHumidity > 95 
            ? "Risco elevado de alagamentos. Evite áreas baixas e esteja preparado para evacuação."
            : avgHumidity > 85 
                ? "Monitore as condições climáticas. Evite estacionar em áreas sujeitas a alagamentos."
                : "Condições normais. Nenhuma ação necessária.";

        Map<String, Object> context = new HashMap<>();
        context.put("generatedAt", Instant.now().toString());
        context.put("avgHumidity", String.format("%.1f", avgHumidity));
        context.put("alertLevel", alertLevel);
        context.put("alertClass", alertClass);
        context.put("recommendation", recommendation);
        context.put("sensors", buildSensorHumidityList(readings));

        if (template != null) {
            return render(template, context);
        }
        return generateFallbackReport("Alerta de Enchente", context);
    }

    private String generatePollutionReport(List<SensorReading> readings) {
        Mustache template = templates.get("report-pollution");
        
        double avgCo2 = average(readings, "co2");
        double avgNo2 = average(readings, "no2");
        double avgPm25 = average(readings, "pm25");
        
        String co2Status = avgCo2 > 1000 ? "RUIM" : avgCo2 > 450 ? "ACEITÁVEL" : "EXCELENTE";
        String co2StatusClass = avgCo2 > 1000 ? "ruim" : avgCo2 > 450 ? "aceitavel" : "excelente";
        String recommendation = avgCo2 > 1000 
            ? "Ar viciado. Abra janelas ou aumente a ventilação do ambiente imediatamente."
            : avgCo2 > 450 
                ? "Níveis aceitáveis. Mantenha boa ventilação para evitar acúmulo."
                : "Qualidade excelente. Ambiente bem ventilado.";
        
        Map<String, Object> context = new HashMap<>();
        context.put("generatedAt", Instant.now().toString());
        context.put("avgCo2", String.format("%.1f", avgCo2));
        context.put("avgNo2", String.format("%.1f", avgNo2));
        context.put("avgPm25", String.format("%.1f", avgPm25));
        context.put("co2Status", co2Status);
        context.put("co2StatusClass", co2StatusClass);
        context.put("recommendation", recommendation);
        context.put("sensors", buildReadingsList(readings));

        if (template != null) {
            return render(template, context);
        }
        return generateFallbackReport("Previsão de Poluição", context);
    }

    private String generateNoiseReport(List<SensorReading> readings) {
        Mustache template = templates.get("report-noise");
        
        double avgNoise = average(readings, "noiseLevel");
        String noiseLevel = avgNoise > 70 ? "alto" : avgNoise > 50 ? "moderado" : "baixo";
        String recommendation = avgNoise > 70 
            ? "Nível de ruído elevado. Use proteção auditiva em exposições prolongadas."
            : avgNoise > 50 
                ? "Nível moderado. Ambiente adequado para trabalho, mas evite exposição contínua."
                : "Ambiente silencioso. Condições ideais para concentração e descanso.";
        
        Map<String, Object> context = new HashMap<>();
        context.put("generatedAt", Instant.now().toString());
        context.put("avgNoise", String.format("%.1f", avgNoise));
        context.put("noiseLevel", noiseLevel);
        context.put("recommendation", recommendation);
        context.put("sensors", buildSensorNoiseList(readings));

        if (template != null) {
            return render(template, context);
        }
        return generateFallbackReport("Mapa de Ruído", context);
    }

    private String generateUVReport(List<SensorReading> readings) {
        Mustache template = templates.get("report-uv");
        
        double avgUv = average(readings, "uvIndex");
        String uvLevel = avgUv >= 11 ? "extremo" : avgUv >= 8 ? "muito-alto" : 
                         avgUv >= 6 ? "alto" : avgUv >= 3 ? "moderado" : "baixo";
        String recommendation = avgUv >= 6 ? "Use protetor solar e evite exposição prolongada" : 
                                "Exposição segura com precauções básicas";
        
        Map<String, Object> context = new HashMap<>();
        context.put("generatedAt", Instant.now().toString());
        context.put("avgUv", String.format("%.1f", avgUv));
        context.put("uvLevel", uvLevel);
        context.put("recommendation", recommendation);
        context.put("sensors", buildSensorUVList(readings));

        if (template != null) {
            return render(template, context);
        }
        return generateFallbackReport("Índice UV", context);
    }

    // ==================== HELPERS ====================

    private String render(Mustache template, Map<String, Object> context) {
        StringWriter writer = new StringWriter();
        template.execute(writer, context);
        return writer.toString();
    }

    private double average(List<SensorReading> readings, String field) {
        return readings.stream()
                .filter(r -> r.data().has(field))
                .mapToDouble(r -> r.data().get(field).getAsDouble())
                .average()
                .orElse(0);
    }

    private List<Map<String, Object>> buildReadingsList(List<SensorReading> readings) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SensorReading r : readings) {
            Map<String, Object> map = new HashMap<>();
            map.put("sensorId", r.sensorId());
            map.put("timestamp", Instant.ofEpochMilli(r.timestamp()).toString());
            map.put("temperature", getFieldFormatted(r.data(), "temperature", "%.1f"));
            map.put("humidity", getFieldFormatted(r.data(), "humidity", "%.1f"));
            map.put("co2", getFieldFormatted(r.data(), "co2", "%.1f"));
            map.put("isAlert", r.isAlert());
            map.put("alertType", r.alertType());
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> buildSensorPollutantList(List<SensorReading> readings) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SensorReading r : readings) {
            Map<String, Object> map = new HashMap<>();
            map.put("sensorId", r.sensorId());
            map.put("pm25", getFieldFormatted(r.data(), "pm25", "%.1f"));
            map.put("pm10", getFieldFormatted(r.data(), "pm10", "%.1f"));
            map.put("co", getFieldFormatted(r.data(), "co", "%.2f"));
            map.put("no2", getFieldFormatted(r.data(), "no2", "%.1f"));
            map.put("so2", getFieldFormatted(r.data(), "so2", "%.1f"));
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> buildSensorHumidityList(List<SensorReading> readings) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SensorReading r : readings) {
            Map<String, Object> map = new HashMap<>();
            map.put("sensorId", r.sensorId());
            map.put("humidity", getFieldFormatted(r.data(), "humidity", "%.1f"));
            map.put("temperature", getFieldFormatted(r.data(), "temperature", "%.1f"));
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> buildSensorNoiseList(List<SensorReading> readings) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SensorReading r : readings) {
            Map<String, Object> map = new HashMap<>();
            map.put("sensorId", r.sensorId());
            map.put("noiseLevel", getFieldFormatted(r.data(), "noiseLevel", "%.1f"));
            double noise = r.data().has("noiseLevel") ? r.data().get("noiseLevel").getAsDouble() : 0;
            map.put("classification", noise > 70 ? "Alto" : noise > 50 ? "Moderado" : "Baixo");
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> buildSensorUVList(List<SensorReading> readings) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SensorReading r : readings) {
            Map<String, Object> map = new HashMap<>();
            map.put("sensorId", r.sensorId());
            map.put("uvIndex", getFieldFormatted(r.data(), "uvIndex", "%.1f"));
            list.add(map);
        }
        return list;
    }

    private String getFieldFormatted(JsonObject data, String field, String format) {
        if (data.has(field)) {
            return String.format(format, data.get(field).getAsDouble());
        }
        return "-";
    }

    // ==================== IQA CALCULATION ====================

    private int calculateIqaPm25(double value) {
        if (value <= 25) return (int)(value * 40 / 25);
        if (value <= 50) return (int)(40 + (value - 25) * 40 / 25);
        return (int)(80 + (value - 50) * 2);
    }

    private int calculateIqaPm10(double value) {
        if (value <= 50) return (int)(value * 40 / 50);
        if (value <= 100) return (int)(40 + (value - 50) * 40 / 50);
        return (int)(80 + (value - 100));
    }

    private int calculateIqaCo(double value) {
        if (value <= 9) return (int)(value * 40 / 9);
        if (value <= 11) return (int)(40 + (value - 9) * 40 / 2);
        return (int)(80 + (value - 11) * 20);
    }

    private int calculateIqaNo2(double value) {
        if (value <= 200) return (int)(value * 40 / 200);
        if (value <= 240) return (int)(40 + (value - 200) * 40 / 40);
        return (int)(80 + (value - 240));
    }

    private int calculateIqaSo2(double value) {
        if (value <= 20) return (int)(value * 40 / 20);
        if (value <= 40) return (int)(40 + (value - 20) * 40 / 20);
        return (int)(80 + (value - 40) * 2);
    }

    private String getMainPollutant(int pm25, int pm10, int co, int no2, int so2) {
        int max = Math.max(Math.max(Math.max(pm25, pm10), Math.max(co, no2)), so2);
        if (max == pm25) return "PM2.5";
        if (max == pm10) return "PM10";
        if (max == co) return "CO";
        if (max == no2) return "NO2";
        return "SO2";
    }

    private String getIqaClassification(int iqa) {
        if (iqa <= 40) return "BOA";
        if (iqa <= 80) return "MODERADA";
        if (iqa <= 120) return "RUIM";
        if (iqa <= 200) return "MUITO RUIM";
        return "PÉSSIMA";
    }

    private String getIqaCssClass(int iqa) {
        if (iqa <= 40) return "boa";
        if (iqa <= 80) return "moderada";
        if (iqa <= 120) return "ruim";
        if (iqa <= 200) return "muito-ruim";
        return "pessima";
    }

    private String getIqaRecommendation(int iqa) {
        if (iqa <= 40) return "Qualidade do ar ideal. Aproveite atividades ao ar livre.";
        if (iqa <= 80) return "Qualidade aceitável. Grupos sensíveis devem limitar esforços prolongados ao ar livre.";
        if (iqa <= 120) return "Qualidade ruim. Evite atividades ao ar livre. Grupos sensíveis devem permanecer em ambientes fechados.";
        if (iqa <= 200) return "Qualidade muito ruim. Todos devem evitar atividades ao ar livre.";
        return "Qualidade péssima. Evite qualquer exposição ao ar livre. Use máscara se necessário sair.";
    }

    // ==================== FALLBACKS ====================

    private String generateFallbackDataTable(List<SensorReading> readings) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>Dados</title></head><body>");
        sb.append("<h1>Dados dos Sensores</h1>");
        sb.append("<p>Total: ").append(readings.size()).append(" leituras</p>");
        sb.append("<table border='1'><tr><th>Sensor</th><th>Timestamp</th><th>Alerta</th></tr>");
        for (SensorReading r : readings) {
            sb.append("<tr><td>").append(r.sensorId()).append("</td>");
            sb.append("<td>").append(Instant.ofEpochMilli(r.timestamp())).append("</td>");
            sb.append("<td>").append(r.isAlert() ? r.alertType() : "-").append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private String generateFallbackReport(String title, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>").append(title).append("</title></head><body>");
        sb.append("<h1>").append(title).append("</h1>");
        sb.append("<p>Gerado em: ").append(context.get("generatedAt")).append("</p>");
        sb.append("<pre>").append(context.toString()).append("</pre>");
        sb.append("</body></html>");
        return sb.toString();
    }
}
