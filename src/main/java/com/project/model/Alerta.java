package com.project.model;

import java.util.ArrayList;
import java.util.List;

import com.project.security.DebugConfig;

public class Alerta {

    private String id;                          // Identificador único do alerta
    private TipoAlerta tipoAlerta;              // Tipo do alerta (qualidade do ar, temperatura, etc.)
    private NivelAlerta nivelAlerta;            // Nível de NivelAlerta do alerta
    private String mensagem;                    // Mensagem descritiva do alerta
    private long timestamp;                     // Momento em que o alerta foi gerado
    private String localizacao;                 // Localização associada ao alerta
    private DadosAmbientais dadosRelacionados;  // Dados ambientais que geraram o alerta

    public Alerta(NivelAlerta nivelAlerta, TipoAlerta tipoAlerta, String mensagem, 
                    DadosAmbientais dadosRelacionados) {
        this.id = "ALERT_" + System.currentTimeMillis();
        this.nivelAlerta = nivelAlerta;
        this.tipoAlerta = tipoAlerta;
        this.mensagem = mensagem;
        this.timestamp = System.currentTimeMillis();
        this.localizacao = dadosRelacionados.getLocalizacao();
        this.dadosRelacionados = dadosRelacionados;
    }

    public String getId() {
        return id;
    }

    public NivelAlerta getNivelAlerta() {
        return nivelAlerta;
    }

    public TipoAlerta getTipoAlerta() {
        return tipoAlerta;
    }

    public String getMensagem() {
        return mensagem;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getLocalizacao() {
        return localizacao;
    }

    public DadosAmbientais getDadosRelacionados() {
        return dadosRelacionados;
    }

    public static List<Alerta> analisar(DadosAmbientais dados) {
        List<Alerta> alertasGerados = new ArrayList<>();

        if (dados == null) {
            return alertasGerados;
        }

        alertasGerados.addAll(analisarQualidadeAr(dados));
        alertasGerados.addAll(analisarTemperatura(dados));
        alertasGerados.addAll(analisarRuido(dados));
        alertasGerados.addAll(analisarRadiacaoUV(dados));
        alertasGerados.addAll(analisarEnchente(dados));

        return alertasGerados;

    }

    private static List<Alerta> analisarQualidadeAr (DadosAmbientais dados) {
        List<Alerta> alertas = new ArrayList<>();


        /**
         * PM2.5 (Partículas finas)
         * - Níveis acima de 150 µg/m³: Crítico
         * - Níveis entre 55-150 µg/m³: Alto
         * - Níveis entre 35-55 µg/m³: Médio
         * - Níveis abaixo de 35 µg/m³: Baixo 
         */
        if (dados.getPm25() > 150) {
            alertas.add(new Alerta(NivelAlerta.CRITICO, TipoAlerta.QUALIDADE_AR, 
                "PM2.5 em nível PÉSSIMO (" + String.format("%.1f", dados.getPm25()) + " µg/m³). Evite atividades ao ar livre!", 
                dados));
        } else if (dados.getPm25() > 55) {
            alertas.add(new Alerta(NivelAlerta.ALTO, TipoAlerta.QUALIDADE_AR, 
                "PM2.5 em nível MUITO RUIM (" + String.format("%.1f", dados.getPm25()) + " µg/m³). Grupos sensíveis devem evitar esforço ao ar livre.", 
                dados));
        } else if (dados.getPm25() > 35) {
            alertas.add(new Alerta(NivelAlerta.MEDIO, TipoAlerta.QUALIDADE_AR, 
                "PM2.5 em nível RUIM (" + String.format("%.1f", dados.getPm25()) + " µg/m³).", 
                dados));
        }

        /**
         * PM10 (Partículas inaláveis)
         * - Níveis acima de 250 µg/m³: Crítico
         * - Níveis entre 150-250 µg/m³: Alto
         * - Níveis entre 75-150 µg/m³: Médio
         * - Níveis abaixo de 75 µg/m³: Baixo
         */

         if (dados.getPm10() > 250) {
            alertas.add(new Alerta(NivelAlerta.CRITICO, TipoAlerta.QUALIDADE_AR, 
                "PM10 em nível PÉSSIMO (" + String.format("%.1f", dados.getPm10()) + " µg/m³).", 
                dados));
        } else if (dados.getPm10() > 150) {
            alertas.add(new Alerta(NivelAlerta.ALTO, TipoAlerta.QUALIDADE_AR, 
                "PM10 em nível MUITO RUIM (" + String.format("%.1f", dados.getPm10()) + " µg/m³).", 
                dados));
        }


        /**
         * CO2 (Dióxido de Carbono)
         * - Níveis acima de 2000 ppm: Crítico
         * - Níveis entre 1000-2000 ppm: Médio
         * - Níveis abaixo de 1000 ppm: Baixo
         */

        if (dados.getCo2() > 2000) {
            alertas.add(new Alerta(NivelAlerta.CRITICO, TipoAlerta.QUALIDADE_AR, 
                "CO2 em nível CRÍTICO (" + String.format("%.0f", dados.getCo2()) + " ppm). Ventilação urgente necessária!", 
                dados));
        } else if (dados.getCo2() > 1000) {
            alertas.add(new Alerta(NivelAlerta.MEDIO, TipoAlerta.QUALIDADE_AR, 
                "CO2 elevado (" + String.format("%.0f", dados.getCo2()) + " ppm). Recomenda-se melhorar ventilação.", 
                dados));
        }

        return alertas;

    }

    private static List<Alerta> analisarTemperatura (DadosAmbientais dados) {
        List<Alerta> alertas = new ArrayList<>();

        /**
         * Temperatura
         * - Acima de 35°C: Crítico
         * - Entre 30-35°C: Alto
         * - Entre 15-30°C: Moderado
         * - Entre 0-15°C: Baixo
         * - Abaixo de 0°C: Crítico
         */

        if (dados.getTemperatura() > 35) {
            alertas.add(new Alerta(NivelAlerta.CRITICO, TipoAlerta.TEMPERATURA, 
                "Temperatura EXTREMA (" + String.format("%.1f", dados.getTemperatura()) + " °C). Risco de insolação!", 
                dados));
        } else if (dados.getTemperatura() < 0) {
            alertas.add(new Alerta(NivelAlerta.CRITICO, TipoAlerta.TEMPERATURA, 
                "Temperatura abaixo de ZERO (" + String.format("%.1f", dados.getTemperatura()) + " °C). Risco de hipotermia!", 
                dados));
        }

        return alertas;
    }

    private static List<Alerta> analisarRuido (DadosAmbientais dados) {
        List<Alerta> alertas = new ArrayList<>();

        /**
         * Ruído
         * - Acima de 100 dB: Crítico
         * - Entre 85-100 dB: Alto
         * - Entre 70-85 dB: Médio
         * - Abaixo de 70 dB: Baixo
         */

        if (dados.getRuido() > 100) {
            alertas.add(new Alerta(NivelAlerta.CRITICO, TipoAlerta.RUIDO, 
                "Ruído EXTREMO (" + String.format("%.1f", dados.getRuido()) + " dB). Risco de dano auditivo imediato!", 
                dados));
        } else if (dados.getRuido() > 85) {
            alertas.add(new Alerta(NivelAlerta.ALTO, TipoAlerta.RUIDO, 
                "Ruído MUITO ALTO (" + String.format("%.1f", dados.getRuido()) + " dB). Exposição prolongada pode causar danos.", 
                dados));
        } else if (dados.getRuido() > 70) {
            alertas.add(new Alerta(NivelAlerta.MEDIO, TipoAlerta.RUIDO, 
                "Ruído ELEVADO (" + String.format("%.1f", dados.getRuido()) + " dB). Pode causar desconforto.", 
                dados));
        }
        
        return alertas;
    }

    private static List<Alerta> analisarRadiacaoUV (DadosAmbientais dados) {
        List<Alerta> alertas = new ArrayList<>();

        /**
         * Radiação UV
         * - Acima de 11: Crítico
         * - Entre 8-11: Alto
         * - Entre 6-8: Médio
         * - Abaixo de 6: Baixo
         */

        if (dados.getRadiacao_uv() >= 11) {
            alertas.add(new Alerta(NivelAlerta.CRITICO, TipoAlerta.RADIACAO_UV, 
                "Índice UV EXTREMO (" + String.format("%.1f", dados.getRadiacao_uv()) + "). Evite exposição ao sol!", 
                dados));
        } else if (dados.getRadiacao_uv() >= 8) {
            alertas.add(new Alerta(NivelAlerta.ALTO, TipoAlerta.RADIACAO_UV, 
                "Índice UV MUITO ALTO (" + String.format("%.1f", dados.getRadiacao_uv()) + "). Use proteção solar.", 
                dados));
        } else if (dados.getRadiacao_uv() >= 6) {
            alertas.add(new Alerta(NivelAlerta.MEDIO, TipoAlerta.RADIACAO_UV, 
                "Índice UV ALTO (" + String.format("%.1f", dados.getRadiacao_uv()) + "). Proteção recomendada.", 
                dados));
        }
        
        return alertas;
    }

    private static List<Alerta> analisarEnchente (DadosAmbientais dados) {
        List<Alerta> alertas = new ArrayList<>();

        /**
         * Umidade
         * - Acima de 95%: Alto
         * - Entre 90-95%: Médio
         * - Abaixo de 90%: Baixo
         */

        if (dados.getUmidade() > 95) {
            alertas.add(new Alerta(NivelAlerta.ALTO, TipoAlerta.ENCHENTE, 
                "Umidade EXTREMA (" + String.format("%.1f", dados.getUmidade()) + "%). Possível risco de alagamento.", 
                dados));
        } else if (dados.getUmidade() > 90) {
            alertas.add(new Alerta(NivelAlerta.MEDIO, TipoAlerta.ENCHENTE, 
                "Umidade MUITO ALTA (" + String.format("%.1f", dados.getUmidade()) + "%). Monitorar condições.", 
                dados));
        }
        
        return alertas;
    }

    @Override
    public String toString() {
        return String.format("Alerta{id='%s', nivel='%s', tipo='%s', mensagem='%s', localizacao='%s', timestamp=%d}",
        id, nivelAlerta, tipoAlerta, mensagem, localizacao, timestamp);
    }

    public enum NivelAlerta {
        CRITICO("Crítico"),         // Situação de perigo, ação imediata necessária
        ALTO("Alto"),               // Risco elevado, ação recomendada
        MEDIO("Moderado"),          // Risco moderado, monitoramento necessário
        BAIXO("Baixo");             // Risco baixo, situação normal

        
        private final String descricao;

        NivelAlerta(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        @Override
        public String toString() {
            return descricao;
        }
    }

    public enum TipoAlerta {
        QUALIDADE_AR("Qualidade do Ar"),    // PM2.5, PM10, CO2
        TEMPERATURA("Temperatura"),         // Temperaturas extremas
        RUIDO("Ruído"),                     // Níveis elevados de ruído
        RADIACAO_UV("Radiação UV"),         // Índice UV alto
        ENCHENTE("Enchente");               // Umidade elevada e risco de alagamento

        private final String descricao;

        TipoAlerta(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        @Override
        public String toString() {
            return descricao;
        }
    }
}