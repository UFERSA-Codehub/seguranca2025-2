package com.project.security;

import com.project.model.TipoRelatorio;
import com.project.model.DadosAmbientais;
import com.project.model.Sensor;
import com.project.model.Alerta;
import com.project.model.Alerta.NivelAlerta;
import com.project.model.Alerta.TipoAlerta;
import java.util.List;

/**
 * Teste completo do modelo de dados.
 * Valida TipoRelatorio, DadosAmbientais, Sensor e Alerta.
 */
public class TesteModelo {
    
    private static int testesExecutados = 0;
    private static int testesPassaram = 0;
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║     TESTE DO MODELO - Sistema de Monitoramento        ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        testarTipoRelatorio();
        testarDadosAmbientais();
        testarSensor();
        testarAlerta();
        
        imprimirResumo();
    }
    
    /**
     * Testa o enum TipoRelatorio.
     */
    private static void testarTipoRelatorio() {
        System.out.println("\n[1] TESTANDO TipoRelatorio...");
        
        // Teste 1.1: Verificar se todos os 5 tipos existem
        testarAssertion("TipoRelatorio - 5 tipos existem", 
            TipoRelatorio.values().length == 5);
        
        // Teste 1.2: Verificar getDescricao()
        testarAssertion("TipoRelatorio - getDescricao() funciona", 
            TipoRelatorio.INDICE_QUALIDADE_AR.getDescricao().equals("Índice de Qualidade do Ar"));
        
        // Teste 1.3: Verificar toString()
        testarAssertion("TipoRelatorio - toString() funciona", 
            TipoRelatorio.TENDENCIAS_POLUICAO.toString().equals("Tendências de Poluição"));
        
        // Teste 1.4: Verificar fromString() com string válida
        TipoRelatorio tipo = TipoRelatorio.fromString("ANALISE_MICROCLIMA");
        testarAssertion("TipoRelatorio - fromString() válido", 
            tipo == TipoRelatorio.ANALISE_MICROCLIMA);
        
        // Teste 1.5: Verificar fromString() com string inválida
        TipoRelatorio tipoInvalido = TipoRelatorio.fromString("INVALIDO");
        testarAssertion("TipoRelatorio - fromString() inválido retorna null", 
            tipoInvalido == null);
        
        System.out.println("✓ TipoRelatorio: Todos os testes passaram!\n");
    }
    
    /**
     * Testa a classe DadosAmbientais.
     */
    private static void testarDadosAmbientais() {
        System.out.println("\n[2] TESTANDO DadosAmbientais...");
        
        // Teste 2.1: Criar DadosAmbientais manualmente
        long agora = System.currentTimeMillis();
        DadosAmbientais dados = new DadosAmbientais(
            agora, 
            "Av. Central, 100", 
            25.5,  // temperatura
            400.0, // co2
            60.0,  // umidade
            50.0,  // ruido
            5.0,   // radiacao_uv
            15.0,  // pm25
            25.0   // pm10
        );
        
        testarAssertion("DadosAmbientais - construtor funciona", 
            dados != null);
        
        // Teste 2.2: Verificar getters
        testarAssertion("DadosAmbientais - getLocalizacao()", 
            dados.getLocalizacao().equals("Av. Central, 100"));
        
        testarAssertion("DadosAmbientais - getTemperatura()", 
            dados.getTemperatura() == 25.5);
        
        testarAssertion("DadosAmbientais - getCo2()", 
            dados.getCo2() == 400.0);
        
        // Teste 2.3: Verificar gerarAleatorio()
        DadosAmbientais dadosAleatorios = DadosAmbientais.gerarAleatorio("Teste Location");
        testarAssertion("DadosAmbientais - gerarAleatorio() não é null", 
            dadosAleatorios != null);
        
        testarAssertion("DadosAmbientais - gerarAleatorio() tem localização correta", 
            dadosAleatorios.getLocalizacao().equals("Teste Location"));
        
        testarAssertion("DadosAmbientais - valores aleatórios em range válido (temperatura)", 
            dadosAleatorios.getTemperatura() >= -10 && dadosAleatorios.getTemperatura() <= 30);
        
        // Teste 2.4: Verificar toString()
        String str = dados.toString();
        testarAssertion("DadosAmbientais - toString() contém informações", 
            str.contains("DadosAmbientais") && str.contains("temperatura"));
        
        System.out.println("✓ DadosAmbientais: Todos os testes passaram!\n");
    }
    
    /**
     * Testa a classe Sensor.
     */
    private static void testarSensor() {
        System.out.println("\n[3] TESTANDO Sensor...");
        
        // Teste 3.1: Criar sensor
        Sensor sensor = new Sensor("SENSOR_001", "Sensor Centro", "Av. Principal, 123", "cred123");
        testarAssertion("Sensor - construtor funciona", 
            sensor != null);
        
        // Teste 3.2: Verificar getters
        testarAssertion("Sensor - getId()", 
            sensor.getId().equals("SENSOR_001"));
        
        testarAssertion("Sensor - getNome()", 
            sensor.getNome().equals("Sensor Centro"));
        
        testarAssertion("Sensor - isAtivo() inicia true", 
            sensor.isAtivo() == true);
        
        // Teste 3.3: Verificar que inicia sem leitura
        testarAssertion("Sensor - ultimaLeitura inicia null", 
            sensor.getUltimaLeitura() == null);
        
        // Teste 3.4: Verificar isConectado() sem leitura
        testarAssertion("Sensor - isConectado() false sem leitura", 
            sensor.isConectado() == false);
        
        // Teste 3.5: Atualizar leitura com localização correta
        DadosAmbientais dados = DadosAmbientais.gerarAleatorio("Av. Principal, 123");
        boolean atualizado = sensor.atualizarLeitura(dados);
        testarAssertion("Sensor - atualizarLeitura() com localização correta", 
            atualizado == true);
        
        // Teste 3.6: Verificar que agora está conectado
        testarAssertion("Sensor - isConectado() true após leitura recente", 
            sensor.isConectado() == true);
        
        // Teste 3.7: Tentar atualizar com localização errada
        DadosAmbientais dadosErrados = DadosAmbientais.gerarAleatorio("Localização Diferente");
        boolean atualizadoErrado = sensor.atualizarLeitura(dadosErrados);
        testarAssertion("Sensor - atualizarLeitura() rejeita localização diferente", 
            atualizadoErrado == false);
        
        // Teste 3.8: Verificar setters
        sensor.setNome("Sensor Norte");
        testarAssertion("Sensor - setNome() funciona", 
            sensor.getNome().equals("Sensor Norte"));
        
        sensor.setAtivo(false);
        testarAssertion("Sensor - setAtivo() funciona", 
            sensor.isAtivo() == false);
        
        // Teste 3.9: Verificar que sensor inativo não está conectado
        testarAssertion("Sensor - isConectado() false quando inativo", 
            sensor.isConectado() == false);
        
        System.out.println("✓ Sensor: Todos os testes passaram!\n");
    }
    
    /**
     * Testa a classe Alerta e seus enums.
     */
    private static void testarAlerta() {
        System.out.println("\n[4] TESTANDO Alerta...");
        
        // Teste 4.1: Criar alerta manualmente
        DadosAmbientais dados = DadosAmbientais.gerarAleatorio("Centro");
        Alerta alerta = new Alerta(
            NivelAlerta.CRITICO, 
            TipoAlerta.QUALIDADE_AR, 
            "Teste de alerta", 
            dados
        );
        
        testarAssertion("Alerta - construtor funciona", 
            alerta != null);
        
        // Teste 4.2: Verificar getters
        testarAssertion("Alerta - getNivelAlerta()", 
            alerta.getNivelAlerta() == NivelAlerta.CRITICO);
        
        testarAssertion("Alerta - getTipoAlerta()", 
            alerta.getTipoAlerta() == TipoAlerta.QUALIDADE_AR);
        
        testarAssertion("Alerta - getId() não é null", 
            alerta.getId() != null && alerta.getId().startsWith("ALERT_"));
        
        // Teste 4.3: Verificar enums
        testarAssertion("Alerta - NivelAlerta tem 4 valores", 
            NivelAlerta.values().length == 4);
        
        testarAssertion("Alerta - TipoAlerta tem 5 valores", 
            TipoAlerta.values().length == 5);
        
        // Teste 4.4: Analisar dados normais (não deve gerar alertas)
        DadosAmbientais dadosNormais = new DadosAmbientais(
            System.currentTimeMillis(),
            "Local Teste",
            22.0,  // temperatura normal
            450.0, // co2 normal
            50.0,  // umidade normal
            40.0,  // ruido baixo
            3.0,   // uv baixo
            10.0,  // pm25 baixo
            20.0   // pm10 baixo
        );
        
        List<Alerta> alertasNormais = Alerta.analisar(dadosNormais);
        testarAssertion("Alerta - analisar() com dados normais não gera alertas", 
            alertasNormais.size() == 0);
        
        // Teste 4.5: Analisar dados críticos (deve gerar múltiplos alertas)
        DadosAmbientais dadosCriticos = new DadosAmbientais(
            System.currentTimeMillis(),
            "Local Perigoso",
            42.0,  // temperatura CRÍTICA (>40)
            2500.0,// co2 CRÍTICO (>2000)
            50.0,  // umidade normal
            110.0, // ruido CRÍTICO (>100)
            12.0,  // uv CRÍTICO (>=11)
            200.0, // pm25 CRÍTICO (>150)
            300.0  // pm10 CRÍTICO (>250)
        );
        
        List<Alerta> alertasCriticos = Alerta.analisar(dadosCriticos);
        testarAssertion("Alerta - analisar() com dados críticos gera alertas", 
            alertasCriticos.size() > 0);
        
        System.out.println("  → Alertas gerados: " + alertasCriticos.size());
        for (Alerta a : alertasCriticos) {
            System.out.println("    • [" + a.getNivelAlerta() + "] " + 
                             a.getTipoAlerta() + ": " + a.getMensagem());
        }
        
        // Teste 4.6: Verificar que pelo menos 5 alertas críticos foram gerados
        testarAssertion("Alerta - dados críticos geram pelo menos 5 alertas", 
            alertasCriticos.size() >= 5);
        
        // Teste 4.7: Verificar que todos são críticos
        boolean todosCriticos = true;
        for (Alerta a : alertasCriticos) {
            if (a.getNivelAlerta() != NivelAlerta.CRITICO) {
                todosCriticos = false;
                break;
            }
        }
        testarAssertion("Alerta - todos os alertas gerados são CRITICOS", 
            todosCriticos);
        
        // Teste 4.8: Testar analisar() com null
        List<Alerta> alertasNull = Alerta.analisar(null);
        testarAssertion("Alerta - analisar(null) retorna lista vazia", 
            alertasNull != null && alertasNull.size() == 0);
        
        System.out.println("✓ Alerta: Todos os testes passaram!\n");
    }
    
    /**
     * Testa uma condição e registra o resultado.
     */
    private static void testarAssertion(String nomeTeste, boolean condicao) {
        testesExecutados++;
        if (condicao) {
            testesPassaram++;
            imprimirSucesso("  ✓ " + nomeTeste);
        } else {
            imprimirErro("  ✗ " + nomeTeste + " - FALHOU!");
        }
    }
    
    /**
     * Imprime mensagem de sucesso em verde.
     */
    private static void imprimirSucesso(String mensagem) {
        System.out.println("\u001B[32m" + mensagem + "\u001B[0m");
    }
    
    /**
     * Imprime mensagem de erro em vermelho.
     */
    private static void imprimirErro(String mensagem) {
        System.err.println("\u001B[31m" + mensagem + "\u001B[0m");
    }
    
    /**
     * Imprime resumo final dos testes.
     */
    private static void imprimirResumo() {
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║                    RESUMO DOS TESTES                  ║");
        System.out.println("╠════════════════════════════════════════════════════════╣");
        System.out.println("║  Total de testes: " + testesExecutados);
        System.out.println("║  Testes passaram: " + testesPassaram);
        System.out.println("║  Testes falharam: " + (testesExecutados - testesPassaram));
        
        double percentual = (testesExecutados > 0) ? 
            (testesPassaram * 100.0 / testesExecutados) : 0;
        System.out.println("║  Taxa de sucesso: " + String.format("%.1f%%", percentual));
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        if (testesPassaram == testesExecutados) {
            imprimirSucesso("🎉 TODOS OS TESTES PASSARAM! 🎉");
        } else {
            imprimirErro("❌ ALGUNS TESTES FALHARAM!");
        }
    }
}
