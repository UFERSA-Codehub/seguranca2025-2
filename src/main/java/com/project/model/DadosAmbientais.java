package com.project.model;

import java.util.Random;

public class DadosAmbientais {
    private long timestamp;
    private String localizacao;
    private double temperatura;
    private double co2;
    private double umidade;
    private double ruido;
    private double radiacao_uv;
    private double pm25;
    private double pm10;

    public DadosAmbientais(long timestamp, String localizacao, double temperatura, double co2, double umidade,
                           double ruido, double radiacao_uv, double pm25, double pm10) {
        this.timestamp = timestamp;
        this.localizacao = localizacao;
        this.temperatura = temperatura;
        this.co2 = co2;
        this.umidade = umidade;
        this.ruido = ruido;
        this.radiacao_uv = radiacao_uv;
        this.pm25 = pm25;
        this.pm10 = pm10;
    }

    // Getters e Setters
    public long getTimestamp() {
        return timestamp;
    }

    public String getLocalizacao() {
        return localizacao;
    }

    public double getTemperatura() {
        return temperatura;
    }

    public double getCo2() {
        return co2;
    }

    public double getUmidade() {
        return umidade;
    }
    public double getRuido() {
        return ruido;
    }
    public double getRadiacao_uv() {
        return radiacao_uv;
    }
    public double getPm25() {
        return pm25;
    }
    public double getPm10() {
        return pm10;
    }

    public static DadosAmbientais gerarAleatorio(String localizacao) {
        Random rand = new Random();
        long timestamp = System.currentTimeMillis();
        double temperatura = -10 + (40) * rand.nextDouble(); // -10 a 30 °C
        double co2 = 300 + (2000) * rand.nextDouble(); // 300 a 2300 ppm
        double umidade = 10 + (90) * rand.nextDouble(); // 10% a 100%
        double ruido = 30 + (100) * rand.nextDouble(); // 30 a 130 dB
        double radiacao_uv = 0 + (11) * rand.nextDouble(); // 0 a 11 índice UV
        double pm25 = 0 + (500) * rand.nextDouble(); // 0 a 500 µg/m³
        double pm10 = 0 + (500) * rand.nextDouble(); // 0 a 500 µg/m³

        return new DadosAmbientais(timestamp, localizacao, temperatura, co2, umidade, ruido, radiacao_uv, pm25, pm10);
    }

    @Override
    public String toString() {
        return String.format("DadosAmbientais{timestamp=%d, localizacao='%s', temperatura=%.2f, co2=%.2f, umidade=%.2f, ruido=%.2f, radiacao_uv=%.2f, pm25=%.2f, pm10=%.2f}",
                timestamp, localizacao, temperatura, co2, umidade, ruido, radiacao_uv, pm25, pm10);
    }
    
}