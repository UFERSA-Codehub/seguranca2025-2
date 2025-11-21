package com.project.model;

public enum TipoRelatorio {
    INDICE_QUALIDADE_AR("Índice de Qualidade do Ar"),
    TENDENCIAS_POLUICAO("Tendências de Poluição"),
    ANALISE_MICROCLIMA("Análise de Microclima"),
    ALERTAS_ENCHENTE("Alertas de Enchente"),
    RECOMENDACOES_TRAFEGO("Recomendações de Tráfego");
    
    private final String descricao;
    
    TipoRelatorio(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    public static TipoRelatorio fromString(String tipo) {
        if (tipo == null) {
            return null;
        }
        
        try {
            return TipoRelatorio.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    @Override
    public String toString() {
        return descricao;
    }
}
