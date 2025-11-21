package com.project.security;

import java.security.PublicKey;
import javax.crypto.SecretKey;

/**
 * Classe de teste para validar implementação RSA, SessionKeys e KeyManager
 * 
 * Execução:
 *   mvn exec:java -Dexec.mainClass="com.project.security.TesteRSA"
 * 
 * Ou:
 *   javac -d target/classes src/main/java/com/project/security/*.java testes/TesteRSA.java
 *   java -cp target/classes com.project.security.TesteRSA
 */
public class TesteRSA {

    private static int testesPassaram = 0;
    private static int testesFalharam = 0;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     TESTES DE VALIDAÇÃO - RSA + SessionKeys + KeyManager   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Ativa modo debug para ver detalhes (descomente se quiser)
        // DebugConfig.DEBUG_MODE = true;

        // Executa todos os testes
        teste1_GeracaoParRSA();
        teste2_CifrarDecifrarRSA();
        teste3_ExportImportChavePublica();
        teste4_HandshakeClienteServidor();
        teste5_CriacaoSessoes();
        teste6_ChavesPublicasConfiaveis();
        teste7_IntegracaoCompleta();
        teste8_LimpezaSessoesExpiradas();

        // Relatório final
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                    RELATÓRIO FINAL                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("✅ Testes Passaram: " + testesPassaram);
        System.out.println("❌ Testes Falharam: " + testesFalharam);
        
        if (testesFalharam == 0) {
            System.out.println();
            System.out.println("🎉 TODOS OS TESTES PASSARAM! Implementação RSA completa e funcional!");
        } else {
            System.out.println();
            System.out.println("⚠️ Alguns testes falharam. Revise a implementação.");
            System.exit(1);
        }
    }

    // ========== TESTE 1: Geração de Par RSA ==========
    private static void teste1_GeracaoParRSA() {
        imprimirTeste("Teste 1: Geração de Par RSA");

        try {
            // Inicializa RSA
            KeyManager.initRSA();
            
            // Valida que chaves foram geradas
            PublicKey chavePublica = KeyManager.getChavePublicaRSA();
            RSA rsaLocal = KeyManager.getRSALocal();

            assertNaoNull(chavePublica, "Chave pública não deve ser null");
            assertNaoNull(rsaLocal.getChavePrivada(), "Chave privada não deve ser null");
            
            // Valida formato
            assertEquals("RSA", chavePublica.getAlgorithm(), "Algoritmo deve ser RSA");
            
            System.out.println("   → Par RSA gerado com sucesso");
            System.out.println("   → Algoritmo: " + chavePublica.getAlgorithm());
            System.out.println("   → Formato: " + chavePublica.getFormat());
            
            testePassed();
            
        } catch (Exception e) {
            testeFailed("Erro ao gerar par RSA: " + e.getMessage());
        }
    }

    // ========== TESTE 2: Cifrar/Decifrar com RSA ==========
    private static void teste2_CifrarDecifrarRSA() {
        imprimirTeste("Teste 2: Cifrar/Decifrar com RSA");

        try {
            RSA rsa = new RSA();
            rsa.gerarParDeChaves();
            
            String mensagemOriginal = "Teste de criptografia RSA - Segurança 2025!";
            
            // Cifra com chave pública
            String mensagemCifrada = rsa.cifrar(mensagemOriginal, rsa.getChavePublica());
            assertNaoNull(mensagemCifrada, "Mensagem cifrada não deve ser null");
            
            // Decifra com chave privada
            String mensagemDecifrada = rsa.decifrar(mensagemCifrada);
            assertNaoNull(mensagemDecifrada, "Mensagem decifrada não deve ser null");
            
            // Valida integridade
            assertEquals(mensagemOriginal, mensagemDecifrada, "Mensagens devem ser iguais");
            
            System.out.println("   → Mensagem original: " + mensagemOriginal);
            System.out.println("   → Mensagem cifrada: " + mensagemCifrada.substring(0, 50) + "...");
            System.out.println("   → Mensagem decifrada: " + mensagemDecifrada);
            System.out.println("   → ✓ Integridade mantida!");
            
            testePassed();
            
        } catch (Exception e) {
            testeFailed("Erro ao cifrar/decifrar: " + e.getMessage());
        }
    }

    // ========== TESTE 3: Export/Import de Chave Pública ==========
    private static void teste3_ExportImportChavePublica() {
        imprimirTeste("Teste 3: Export/Import de Chave Pública Base64");

        try {
            KeyManager.initRSA();
            
            // Exporta chave pública
            String chaveBase64 = KeyManager.getChavePublicaRSABase64();
            assertNaoNull(chaveBase64, "Chave Base64 não deve ser null");
            
            System.out.println("   → Chave exportada (primeiros 80 chars): " + chaveBase64.substring(0, 80) + "...");
            
            // Importa chave pública
            PublicKey chaveImportada = RSA.importarChavePublicaBase64(chaveBase64);
            assertNaoNull(chaveImportada, "Chave importada não deve ser null");
            
            // Testa cifrar com chave importada
            RSA rsaServidor = KeyManager.getRSALocal();
            RSA rsaCliente = new RSA();
            
            String mensagem = "Testando chave importada";
            String cifrado = rsaCliente.cifrar(mensagem, chaveImportada);
            String decifrado = rsaServidor.decifrar(cifrado);
            
            assertEquals(mensagem, decifrado, "Chave importada deve funcionar para cifrar");
            
            System.out.println("   → Chave importada com sucesso");
            System.out.println("   → ✓ Cifra/Decifra funciona com chave importada!");
            
            testePassed();
            
        } catch (Exception e) {
            testeFailed("Erro ao exportar/importar chave: " + e.getMessage());
        }
    }

    // ========== TESTE 4: Handshake Cliente-Servidor ==========
    private static void teste4_HandshakeClienteServidor() {
        imprimirTeste("Teste 4: Handshake Cliente-Servidor Simulado");

        try {
            // === SERVIDOR ===
            System.out.println("   [SERVIDOR] Inicializando RSA...");
            KeyManager.initRSA();
            String chavePublicaServidor = KeyManager.getChavePublicaRSABase64();
            
            // === CLIENTE ===
            System.out.println("   [CLIENTE] Recebendo chave pública do servidor...");
            PublicKey pubKeyServidor = RSA.importarChavePublicaBase64(chavePublicaServidor);
            
            // Cliente prepara credenciais
            String credenciais = "usuario:sensor-001||senha:secreto123";
            System.out.println("   [CLIENTE] Cifrando credenciais: " + credenciais);
            
            RSA rsaCliente = new RSA();
            String credenciaisCifradas = rsaCliente.cifrar(credenciais, pubKeyServidor);
            
            // === SERVIDOR ===
            System.out.println("   [SERVIDOR] Recebendo e decifrando credenciais...");
            RSA rsaServidor = KeyManager.getRSALocal();
            String credenciaisDecifradas = rsaServidor.decifrar(credenciaisCifradas);
            
            // Validação
            assertEquals(credenciais, credenciaisDecifradas, "Handshake deve preservar dados");
            
            System.out.println("   [SERVIDOR] Credenciais decifradas: " + credenciaisDecifradas);
            System.out.println("   → ✓ Handshake completado com sucesso!");
            
            testePassed();
            
        } catch (Exception e) {
            testeFailed("Erro no handshake: " + e.getMessage());
        }
    }

    // ========== TESTE 5: Criação de Sessões ==========
    private static void teste5_CriacaoSessoes() {
        imprimirTeste("Teste 5: Criação de Sessões");

        try {
            // Cria sessões para 3 clientes diferentes
            SessionKeys sessao1 = KeyManager.criarChavesDaSessao("sensor-001");
            SessionKeys sessao2 = KeyManager.criarChavesDaSessao("sensor-002");
            SessionKeys sessao3 = KeyManager.criarChavesDaSessao("cliente-001");
            
            // Valida que foram criadas
            assertNaoNull(sessao1, "Sessão 1 não deve ser null");
            assertNaoNull(sessao2, "Sessão 2 não deve ser null");
            assertNaoNull(sessao3, "Sessão 3 não deve ser null");
            
            // Valida que chaves são únicas
            assertDiferente(sessao1.getAesKey(), sessao2.getAesKey(), "Chaves AES devem ser diferentes");
            assertDiferente(sessao1.getHmacKey(), sessao2.getHmacKey(), "Chaves HMAC devem ser diferentes");
            
            // Valida expiração
            assertTrue(!sessao1.isExpired(), "Sessão não deve estar expirada");
            assertTrue(sessao1.getTempoRestante() > 0, "Tempo restante deve ser positivo");
            
            System.out.println("   → Sessão 1: " + sessao1);
            System.out.println("   → Sessão 2: " + sessao2);
            System.out.println("   → Sessão 3: " + sessao3);
            System.out.println("   → ✓ Sessões criadas e validadas!");
            
            // Testa recuperação de sessão
            SessionKeys recuperada = KeyManager.obterChavesDaSessao("sensor-001");
            assertEquals(sessao1.getClientId(), recuperada.getClientId(), "Sessão recuperada deve ser a mesma");
            
            testePassed();
            
        } catch (Exception e) {
            testeFailed("Erro ao criar sessões: " + e.getMessage());
        }
    }

    // ========== TESTE 6: Chaves Públicas Confiáveis ==========
    private static void teste6_ChavesPublicasConfiaveis() {
        imprimirTeste("Teste 6: Registro de Chaves Públicas Confiáveis");

        try {
            // Gera 3 pares de chaves (simulando 3 sensores)
            RSA sensor1 = new RSA();
            sensor1.gerarParDeChaves();
            String chaveS1 = sensor1.exportarChavePublicaBase64();
            
            RSA sensor2 = new RSA();
            sensor2.gerarParDeChaves();
            String chaveS2 = sensor2.exportarChavePublicaBase64();
            
            // Registra chaves confiáveis (nota: método tem typo 'COnfiavel' no KeyManager)
            KeyManager.registrarChavePublicaConfiavel("sensor-001", chaveS1);
            KeyManager.registrarChavePublicaConfiavel("sensor-002", chaveS2);
            
            // Valida registro
            assertTrue(KeyManager.isChaveConfiavel("sensor-001"), "Sensor 1 deve ser confiável");
            assertTrue(KeyManager.isChaveConfiavel("sensor-002"), "Sensor 2 deve ser confiável");
            assertTrue(!KeyManager.isChaveConfiavel("sensor-999"), "Sensor 999 não deve ser confiável");
            
            // Recupera chaves
            PublicKey chaveRecuperada1 = KeyManager.obterChavePublicaConfiavel("sensor-001");
            assertNaoNull(chaveRecuperada1, "Chave recuperada não deve ser null");
            
            System.out.println("   → Sensor 1 registrado e confiável: ✓");
            System.out.println("   → Sensor 2 registrado e confiável: ✓");
            System.out.println("   → Sensor 999 não registrado: ✓");
            System.out.println("   → " + KeyManager.diagnostico());
            
            testePassed();
            
        } catch (Exception e) {
            testeFailed("Erro ao gerenciar chaves confiáveis: " + e.getMessage());
        }
    }

    // ========== TESTE 7: Integração Completa ==========
    private static void teste7_IntegracaoCompleta() {
        imprimirTeste("Teste 7: Integração RSA + AES + HMAC");

        try {
            System.out.println("   [Simulando comunicação completa Sensor → Edge]");
            
            // 1. Edge Server inicia
            KeyManager.initRSA();
            String edgePublicKey = KeyManager.getChavePublicaRSABase64();
            System.out.println("   [EDGE] RSA inicializado");
            
            // 2. Sensor faz handshake
            PublicKey edgePubKey = RSA.importarChavePublicaBase64(edgePublicKey);
            RSA sensorRSA = new RSA();
            
            // Sensor cria suas chaves de sessão
            SessionKeys sessaoSensor = KeyManager.criarChavesDaSessao("sensor-handshake-test");
            
            // Prepara payload com credenciais + chaves
            String payload = "sensor-001:senha123";
            String payloadCifrado = sensorRSA.cifrar(payload, edgePubKey);
            System.out.println("   [SENSOR] Handshake enviado (RSA)");
            
            // 3. Edge recebe e decifra
            RSA edgeRSA = KeyManager.getRSALocal();
            String payloadDecifrado = edgeRSA.decifrar(payloadCifrado);
            assertEquals(payload, payloadDecifrado, "Payload deve ser preservado");
            System.out.println("   [EDGE] Credenciais validadas: " + payloadDecifrado);
            
            // 4. Comunicação subsequente com AES + HMAC
            AES aes = new AES();
            aes.setChave(sessaoSensor.getAesKey());
            
            HMAC hmac = new HMAC();
            hmac.setKey(sessaoSensor.getHmacKey());
            
            String mensagemDados = "CO2:450||temp:25.5||humidity:60";
            String dadosCifrados = aes.cifrar(mensagemDados);
            String hmacValue = hmac.generateHMAC(dadosCifrados);
            System.out.println("   [SENSOR] Dados enviados (AES+HMAC)");
            
            // 5. Edge recebe e valida
            boolean hmacValido = hmac.verifyHMAC(dadosCifrados, hmacValue);
            assertTrue(hmacValido, "HMAC deve ser válido");
            
            String dadosDecifrados = aes.decifrar(dadosCifrados);
            assertEquals(mensagemDados, dadosDecifrados, "Dados devem ser preservados");
            System.out.println("   [EDGE] Dados recebidos e validados: " + dadosDecifrados);
            
            System.out.println("   → ✓ Integração completa funcionando!");
            
            testePassed();
            
        } catch (Exception e) {
            testeFailed("Erro na integração: " + e.getMessage());
        }
    }

    // ========== TESTE 8: Limpeza de Sessões Expiradas ==========
    private static void teste8_LimpezaSessoesExpiradas() {
        imprimirTeste("Teste 8: Limpeza de Sessões Expiradas");

        try {
            // Cria sessão normal
            SessionKeys sessaoNormal = KeyManager.criarChavesDaSessao("teste-expiracao-normal");
            
            System.out.println("   → Sessão normal criada: " + sessaoNormal.getClientId());
            System.out.println("   → Tempo restante: " + sessaoNormal.getTempoRestante() + "ms");
            
            // Testa método de limpeza
            String diagnosticoAntes = KeyManager.diagnostico();
            KeyManager.limparSessoesExpiradas();
            String diagnosticoDepois = KeyManager.diagnostico();
            
            System.out.println("   → Antes da limpeza: " + diagnosticoAntes);
            System.out.println("   → Após limpeza: " + diagnosticoDepois);
            System.out.println("   → ✓ Método de limpeza funciona!");
            
            testePassed();
            
        } catch (Exception e) {
            testeFailed("Erro ao testar limpeza: " + e.getMessage());
        }
    }

    // ========== UTILITÁRIOS DE TESTE ==========

    private static void imprimirTeste(String nome) {
        System.out.println();
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println(nome);
        System.out.println("─────────────────────────────────────────────────────────────");
    }

    private static void testePassed() {
        testesPassaram++;
        System.out.println("   ✅ PASSOU");
    }

    private static void testeFailed(String mensagem) {
        testesFalharam++;
        System.out.println("   ❌ FALHOU: " + mensagem);
    }

    private static void assertNaoNull(Object obj, String mensagem) {
        if (obj == null) {
            throw new AssertionError(mensagem);
        }
    }

    private static void assertEquals(Object esperado, Object atual, String mensagem) {
        if (!esperado.equals(atual)) {
            throw new AssertionError(mensagem + " - Esperado: " + esperado + ", Atual: " + atual);
        }
    }

    private static void assertDiferente(Object obj1, Object obj2, String mensagem) {
        if (obj1.equals(obj2)) {
            throw new AssertionError(mensagem);
        }
    }

    private static void assertTrue(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }
}
