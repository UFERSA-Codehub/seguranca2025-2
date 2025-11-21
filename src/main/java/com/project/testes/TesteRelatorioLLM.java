package com.project.testes;

import com.project.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Teste de integração do Relatório com LLM.
 */
public class TesteRelatorioLLM {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║         TESTE DE RELATÓRIO COM LLM (GEMINI)          ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        try {
            // Teste 1: Relatório com dados NORMAIS
            System.out.println("📋 TESTE 1: Dados Normais (Qualidade Boa)\n");
            testarRelatorioNormal();
            
            System.out.println("\n" + "=".repeat(60) + "\n");
            
            // Teste 2: Relatório com dados CRÍTICOS
            System.out.println("📋 TESTE 2: Dados Críticos (Qualidade Péssima)\n");
            testarRelatorioCritico();
            
            System.out.println("\n" + "=".repeat(60) + "\n");
            
            // Teste 3: Relatório de Microclima
            System.out.println("📋 TESTE 3: Análise de Microclima\n");
            testarRelatorioMicroclima();
            
            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║              ✅ TODOS OS TESTES PASSARAM!             ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            System.err.println("\n❌ ERRO NO TESTE:");
            System.err.println("Mensagem: " + e.getMessage());
            e.printStackTrace();
            
            System.err.println("\n💡 POSSÍVEIS CAUSAS:");
            System.err.println("1. API Key não configurada no .env");
            System.err.println("2. Sem conexão com internet");
            System.err.println("3. Quota da API esgotada");
            System.err.println("4. Formato de resposta da LLM mudou");
        }
    }
    
    /**
     * Testa relatório com dados normais (qualidade boa).
     */
    private static void testarRelatorioNormal() {
        List<DadosAmbientais> dados = new ArrayList<>();
        
        // Gerar 5 leituras com valores NORMAIS
        for (int i = 0; i < 5; i++) {
            DadosAmbientais d = new DadosAmbientais(
                System.currentTimeMillis() + (i * 1000),
                "Parque Central",
                22.0 + (Math.random() * 3),  // 22-25°C
                450.0 + (Math.random() * 50), // 450-500 ppm CO2
                55.0 + (Math.random() * 10),  // 55-65% umidade
                45.0 + (Math.random() * 10),  // 45-55 dB
                4.0 + (Math.random() * 2),    // 4-6 UV
                8.0 + (Math.random() * 4),    // 8-12 µg/m³ PM2.5
                15.0 + (Math.random() * 10)   // 15-25 µg/m³ PM10
            );
            dados.add(d);
        }
        
        System.out.println("🔄 Gerando relatório (aguarde resposta da LLM)...\n");
        
        long inicio = System.currentTimeMillis();
        Relatorio relatorio = Relatorio.gerar(TipoRelatorio.INDICE_QUALIDADE_AR, dados);
        long duracao = System.currentTimeMillis() - inicio;
        
        System.out.println(relatorio);
        System.out.println("⏱️  Tempo de geração: " + duracao + "ms");
    }
    
    /**
     * Testa relatório com dados críticos (qualidade péssima).
     */
    private static void testarRelatorioCritico() {
        List<DadosAmbientais> dados = new ArrayList<>();
        
        // Gerar 5 leituras com valores CRÍTICOS
        for (int i = 0; i < 5; i++) {
            DadosAmbientais d = new DadosAmbientais(
                System.currentTimeMillis() + (i * 1000),
                "Avenida Industrial",
                38.0 + (Math.random() * 4),   // 38-42°C (QUENTE!)
                2200.0 + (Math.random() * 300), // 2200-2500 ppm CO2 (CRÍTICO!)
                45.0 + (Math.random() * 10),   // 45-55% umidade
                95.0 + (Math.random() * 10),   // 95-105 dB (MUITO ALTO!)
                11.0 + (Math.random() * 2),    // 11-13 UV (EXTREMO!)
                180.0 + (Math.random() * 40),  // 180-220 µg/m³ PM2.5 (PÉSSIMO!)
                280.0 + (Math.random() * 40)   // 280-320 µg/m³ PM10 (PÉSSIMO!)
            );
            dados.add(d);
        }
        
        System.out.println("🔄 Gerando relatório (aguarde resposta da LLM)...\n");
        
        long inicio = System.currentTimeMillis();
        Relatorio relatorio = Relatorio.gerar(TipoRelatorio.INDICE_QUALIDADE_AR, dados);
        long duracao = System.currentTimeMillis() - inicio;
        
        System.out.println(relatorio);
        System.out.println("⏱️  Tempo de geração: " + duracao + "ms");
        
        // Verificar se detectou alertas
        if (relatorio.getAlertas().size() > 0) {
            System.out.println("\n🚨 ALERTAS DETECTADOS:");
            for (int i = 0; i < Math.min(3, relatorio.getAlertas().size()); i++) {
                Alerta a = relatorio.getAlertas().get(i);
                System.out.println("   • [" + a.getNivelAlerta() + "] " + a.getTipoAlerta() + ": " + a.getMensagem());
            }
            if (relatorio.getAlertas().size() > 3) {
                System.out.println("   ... e mais " + (relatorio.getAlertas().size() - 3) + " alertas");
            }
        }
    }
    
    /**
     * Testa relatório de microclima.
     */
    private static void testarRelatorioMicroclima() {
        List<DadosAmbientais> dados = new ArrayList<>();
        
        // Gerar 8 leituras ao longo do dia (temperatura variando)
        for (int i = 0; i < 8; i++) {
            double temperatura = 18.0 + (i * 2.5); // 18°C até 35.5°C
            DadosAmbientais d = new DadosAmbientais(
                System.currentTimeMillis() + (i * 1000),
                "Praça da Cidade",
                temperatura,
                400.0 + (Math.random() * 100),
                60.0 - (i * 3),  // Umidade diminui durante o dia
                50.0 + (Math.random() * 10),
                i + (Math.random() * 2),  // UV aumenta durante o dia
                12.0 + (Math.random() * 5),
                20.0 + (Math.random() * 10)
            );
            dados.add(d);
        }
        
        System.out.println("🔄 Gerando relatório (aguarde resposta da LLM)...\n");
        
        long inicio = System.currentTimeMillis();
        Relatorio relatorio = Relatorio.gerar(TipoRelatorio.ANALISE_MICROCLIMA, dados);
        long duracao = System.currentTimeMillis() - inicio;
        
        System.out.println(relatorio);
        System.out.println("⏱️  Tempo de geração: " + duracao + "ms");
    }
}
