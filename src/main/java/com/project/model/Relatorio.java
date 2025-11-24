package com.project.model;
import com.project.service.LLMService;
import com.project.service.LLMService.RelatorioContext;
import com.project.service.LLMService.RelatorioLLM;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Relatorio {
    
    private String id;
    private TipoRelatorio tipo;
    private long dataGeracao;
    private List<DadosAmbientais> dados;
    private List<Alerta> alertas;
    
    // Estatísticas calculadas localmente
    private double iqa;
    private double mediaPm25;
    private double mediaPm10;
    private double mediaCo2;
    private double mediaTemperatura;
    private double mediaUmidade;
    private double mediaRuido;
    private double mediaUV;
    
    // Texto gerado pela LLM
    private String conclusao;
    private List<String> recomendacoes;

    private Relatorio(TipoRelatorio tipo, List<DadosAmbientais> dados) {
        this.id = "REL_" + System.currentTimeMillis();
        this.tipo = tipo;
        this.dataGeracao = System.currentTimeMillis();
        this.dados = dados;
        this.alertas = new ArrayList<>();
        this.recomendacoes = new ArrayList<>();
    }

    public static Relatorio gerar(TipoRelatorio tipo, List<DadosAmbientais> dados) {
        if (tipo == null || dados == null || dados.isEmpty()) {
            throw new IllegalArgumentException("Tipo e dados não podem ser nulos ou vazios");
        }
        
        Relatorio rel = new Relatorio(tipo, dados);
        
        // Fase 1: Cálculos locais (rápido, preciso)
        rel.calcularEstatisticas();
        rel.coletarAlertas();
        
        // Fase 2: Gerar texto com LLM
        try {
            rel.gerarTextoComLLM();
        } catch (Exception e) {
            System.err.println("[Relatorio] Erro ao chamar LLM: " + e.getMessage());
            rel.conclusao = "Erro ao gerar relatório: " + e.getMessage();
            rel.recomendacoes.add("Verifique configuração da API LLM");
        }
        
        return rel;
    }

    private void calcularEstatisticas() {
        double somaPm25 = 0, somaPm10 = 0, somaCo2 = 0;
        double somaTemp = 0, somaUmid = 0, somaRuido = 0, somaUV = 0;
        
        for (DadosAmbientais d : dados) {
            somaPm25 += d.getPm25();
            somaPm10 += d.getPm10();
            somaCo2 += d.getCo2();
            somaTemp += d.getTemperatura();
            somaUmid += d.getUmidade();
            somaRuido += d.getRuido();
            somaUV += d.getRadiacao_uv();
        }
        
        int n = dados.size();
        this.mediaPm25 = somaPm25 / n;
        this.mediaPm10 = somaPm10 / n;
        this.mediaCo2 = somaCo2 / n;
        this.mediaTemperatura = somaTemp / n;
        this.mediaUmidade = somaUmid / n;
        this.mediaRuido = somaRuido / n;
        this.mediaUV = somaUV / n;
        
        // Calcular IQA
        double iqaPm25 = calcularIQA(mediaPm25, "PM2.5");
        double iqaPm10 = calcularIQA(mediaPm10, "PM10");
        double iqaCo2 = calcularIQA(mediaCo2, "CO2");
        this.iqa = Math.max(iqaPm25, Math.max(iqaPm10, iqaCo2));
    }

    private void coletarAlertas() {
        for (DadosAmbientais d : dados) {
            alertas.addAll(Alerta.analisar(d));
        }
    }

    private void gerarTextoComLLM() throws Exception {
        // Montar contexto
        RelatorioContext contexto = new RelatorioContext();
        contexto.tipo = tipo.getDescricao();
        contexto.numLeituras = dados.size();
        contexto.iqa = iqa;
        contexto.classificacaoIQA = classificarIQA(iqa);
        contexto.mediaPm25 = mediaPm25;
        contexto.mediaPm10 = mediaPm10;
        contexto.mediaCo2 = mediaCo2;
        contexto.mediaTemperatura = mediaTemperatura;
        contexto.mediaUmidade = mediaUmidade;
        contexto.mediaRuido = mediaRuido;
        contexto.mediaUV = mediaUV;
        contexto.numAlertas = alertas.size();
        contexto.alertasAmostra = new ArrayList<>();
        
        // Incluir amostra de alertas (máx 5)
        for (int i = 0; i < Math.min(5, alertas.size()); i++) {
            Alerta a = alertas.get(i);
            contexto.alertasAmostra.add("[" + a.getNivelAlerta() + "] " + a.getMensagem());
        }
        
        // Chamar LLM
        RelatorioLLM resultado = LLMService.gerarRelatorio(contexto);
        
        this.conclusao = resultado.conclusao;
        this.recomendacoes = resultado.recomendacoes;
    }

    private double calcularIQA(double concentracao, String poluente) {
        double[][] breakpoints;
        
        if (poluente.equals("PM2.5")) {
            breakpoints = new double[][] {
                {0, 12, 0, 50}, {12.1, 35.4, 51, 100}, {35.5, 55.4, 101, 150},
                {55.5, 150.4, 151, 200}, {150.5, 250.4, 201, 300}, {250.5, 500, 301, 500}
            };
        } else if (poluente.equals("PM10")) {
            breakpoints = new double[][] {
                {0, 54, 0, 50}, {55, 154, 51, 100}, {155, 254, 101, 150},
                {255, 354, 151, 200}, {355, 424, 201, 300}, {425, 604, 301, 500}
            };
        } else {
            breakpoints = new double[][] {
                {0, 600, 0, 50}, {601, 1000, 51, 100}, {1001, 1500, 101, 150},
                {1501, 2000, 151, 200}, {2001, 5000, 201, 300}, {5001, 10000, 301, 500}
            };
        }
        
        for (double[] bp : breakpoints) {
            if (concentracao >= bp[0] && concentracao <= bp[1]) {
                return ((bp[3] - bp[2]) / (bp[1] - bp[0])) * (concentracao - bp[0]) + bp[2];
            }
        }
        return 500;
    }
    
    private String classificarIQA(double iqa) {
        if (iqa <= 50) return "BOA";
        if (iqa <= 100) return "MODERADA";
        if (iqa <= 150) return "RUIM";
        if (iqa <= 200) return "MUITO RUIM";
        return "PÉSSIMA";
    }
    
    // Getters
    public String getId() { return id; }
    public TipoRelatorio getTipo() { return tipo; }
    public long getDataGeracao() { return dataGeracao; }
    public List<DadosAmbientais> getDados() { return dados; }
    public List<Alerta> getAlertas() { return alertas; }
    public String getConclusao() { return conclusao; }
    public List<String> getRecomendacoes() { return recomendacoes; }
    public double getIqa() { return iqa; }
    public String getClassificacaoIQA() { return classificarIQA(iqa); }
    public double getMediaPm25() { return mediaPm25; }
    public double getMediaPm10() { return mediaPm10; }
    public double getMediaCo2() { return mediaCo2; }
    public double getMediaTemperatura() { return mediaTemperatura; }
    public double getMediaUmidade() { return mediaUmidade; }
    public double getMediaRuido() { return mediaRuido; }
    public double getMediaUV() { return mediaUV; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔═══════════════════════════════════════════════════════════╗\n");
        sb.append("║  RELATÓRIO: ").append(tipo.getDescricao()).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════╣\n");
        sb.append("  ID: ").append(id).append("\n");
        sb.append("  Data: ").append(formatarData(dataGeracao)).append("\n");
        sb.append("  IQA: ").append(String.format("%.0f", iqa)).append(" (").append(classificarIQA(iqa)).append(")\n");
        sb.append("╚═══════════════════════════════════════════════════════════╝\n\n");
        
        sb.append("📊 CONCLUSÃO:\n");
        sb.append(conclusao).append("\n\n");
        
        sb.append("💡 RECOMENDAÇÕES:\n");
        for (int i = 0; i < recomendacoes.size(); i++) {
            sb.append("  ").append((i + 1)).append(". ").append(recomendacoes.get(i)).append("\n");
        }
        
        sb.append("\n⚠️  ALERTAS: ").append(alertas.size()).append(" detectados\n");
        
        return sb.toString();
    }
    
    private String formatarData(long timestamp) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp));
    }
}