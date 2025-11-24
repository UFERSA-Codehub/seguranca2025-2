package com.project.client;

import com.project.client.config.ClientConfig;
import java.util.List;
import java.util.Scanner;

public class ClienteApp {

    private ClientImpl client;
    private Scanner scanner;
    private boolean executando;
    private ClientConfig config;

    public ClienteApp() {
        this.config = new ClientConfig();
        this.client = new ClientImpl(config);
        this.scanner = new Scanner(System.in);
        this.executando = true;
    }

    public void iniciar() {
        exibirBanner();
        
        while (executando) {
            exibirMenu();
            int opcao = lerOpcao();
            processarOpcao(opcao);
        }
        
        scanner.close();
        System.out.println("\n👋 Encerrando cliente. Até logo!");
    }

    private void exibirBanner() {
        System.out.println("╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║        SISTEMA DE MONITORAMENTO AMBIENTAL - CLIENTE              ║");
        System.out.println("║                    Modo Interativo                                ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void exibirMenu() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                      MENU PRINCIPAL                               ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════╣");
        
        // Status da conexão
        if (!client.isAutenticado()) {
            System.out.println("║  Status: ⚠️  NÃO AUTENTICADO                                     ║");
        } else {
            System.out.println("║  Status: ✅ AUTENTICADO                                          ║");
            System.out.println("║  Datacenter: " + formatarTexto(client.getDatacenterInfo(), 47) + " ║");
        }
        
        System.out.println("╠═══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  CONFIGURAÇÃO                                                     ║");
        System.out.println("║    1. Descobrir Datacenter via Discovery Service                 ║");
        System.out.println("║    2. Autenticar (Login com JWT)                                 ║");
        System.out.println("║    3. Consultar Status do Servidor                               ║");
        System.out.println("║                                                                   ║");
        System.out.println("║  RELATÓRIOS AMBIENTAIS                                            ║");
        System.out.println("║    4. 🌍 Índice de Qualidade do Ar (IQA)                         ║");
        System.out.println("║    5. 📈 Tendências de Poluição                                  ║");
        System.out.println("║    6. 🌡️  Análise de Microclima                                  ║");
        System.out.println("║    7. 🌊 Alertas de Enchente                                     ║");
        System.out.println("║    8. 🚦 Recomendações de Tráfego                                ║");
        System.out.println("║                                                                   ║");
        System.out.println("║  FERRAMENTAS                                                      ║");
        System.out.println("║    9. 🔍 Inspecionar Token JWT                                   ║");
        System.out.println("║    10. 📋 Mostrar Credenciais Disponíveis                        ║");
        System.out.println("║    11. 🗑️  Limpar Cache de Token                                 ║");
        System.out.println("║                                                                   ║");
        System.out.println("║    0. Sair                                                        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
        System.out.print("\n➤ Escolha uma opção: ");
    }

    private int lerOpcao() {
        try {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) return -1;
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void processarOpcao(int opcao) {
        System.out.println();
        
        try {
            switch (opcao) {
                case 0:
                    executando = false;
                    break;
                case 1:
                    descobrirDatacenter();
                    break;
                case 2:
                    autenticar();
                    break;
                case 3:
                    consultarStatus();
                    break;
                case 4:
                    consultarIQA();
                    break;
                case 5:
                    consultarTendencias();
                    break;
                case 6:
                    consultarMicroclima();
                    break;
                case 7:
                    consultarEnchentes();
                    break;
                case 8:
                    consultarTrafego();
                    break;
                case 9:
                    inspecionarToken();
                    break;
                case 10:
                    mostrarCredenciais();
                    break;
                case 11:
                    limparCache();
                    break;
                default:
                    System.out.println("❌ Opção inválida! Tente novamente.");
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao processar opção: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Token inválido")) {
                System.out.println("💡 Dica: Faça login novamente (opção 2)");
            }
        }
        
        pausar();
    }
    
    // ═══════════════════════════════════════════════════════════════
    // OPÇÕES DO MENU
    // ═══════════════════════════════════════════════════════════════

    private void descobrirDatacenter() {
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  🔍 DESCOBRINDO DATACENTER VIA DISCOVERY SERVICE            │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        boolean sucesso = client.descobrirDatacenter();
        
        if (sucesso) {
            System.out.println("\n✅ Datacenter descoberto com sucesso!");
            System.out.println("   📍 Endereço: " + client.getDatacenterInfo());
            System.out.println("\n💡 Próximo passo: Faça login (opção 2)");
        } else {
            System.out.println("\n❌ Falha ao descobrir Datacenter!");
            System.out.println("   Verifique se o Discovery Service está rodando.");
        }
    }

    private void autenticar() {
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  🔐 AUTENTICAÇÃO JWT                                         │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        // Verificar se descobriu datacenter
        if (client.getDatacenterInfo().equals("Não conectado")) {
            System.out.println("⚠️  Você precisa descobrir o Datacenter primeiro (opção 1)!");
            return;
        }
        
        // Ler credenciais
        System.out.print("👤 Usuário: ");
        String usuario = scanner.nextLine().trim();
        
        System.out.print("🔑 Senha: ");
        String senha = scanner.nextLine().trim();
        
        if (usuario.isEmpty() || senha.isEmpty()) {
            System.out.println("❌ Usuário e senha não podem estar vazios!");
            return;
        }
        
        System.out.println("\n🔄 Autenticando...");
        boolean sucesso = client.autenticar(usuario, senha);
        
        if (sucesso) {
            System.out.println("\n✅ Autenticação bem-sucedida!");
            System.out.println("   👤 Usuário: " + usuario);
            System.out.println("   🎫 Token obtido (válido por 24h)");
            System.out.println("\n💡 Agora você pode consultar relatórios (opções 4-8)");
        } else {
            System.out.println("\n❌ Falha na autenticação!");
            System.out.println("   Verifique suas credenciais.");
        }
    }

    private void consultarStatus() {
        if (!validarAutenticacao()) return;
        
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  📊 STATUS DO SERVIDOR                                       │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        try {
            String resposta = client.consultarStatus();
            
            // Extrair informações
            String status = ClienteHTTP.extrairCampo(resposta, "status");
            String totalSensores = ClienteHTTP.extrairCampo(resposta, "totalSensores");
            String totalLeituras = ClienteHTTP.extrairCampo(resposta, "totalLeituras");
            
            System.out.println("  Status:           " + (status != null ? status : "N/A"));
            System.out.println("  Sensores ativos:  " + (totalSensores != null ? totalSensores : "N/A"));
            System.out.println("  Total de leituras: " + (totalLeituras != null ? totalLeituras : "N/A"));
            System.out.println("\n  JSON completo:");
            System.out.println("  " + resposta);
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao consultar status: " + e.getMessage());
        }
    }

    private void consultarIQA() {
        if (!validarAutenticacao()) return;
        
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  🌍 ÍNDICE DE QUALIDADE DO AR (IQA)                         │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        long[] periodo = perguntarPeriodo();
        
        try {
            System.out.println("🔄 Consultando...");
            String resposta = client.consultarIQA(periodo[0], periodo[1]);
            exibirRelatorioIQA(resposta);
        } catch (Exception e) {
            tratarErroConsulta(e);
        }
    }

    private void consultarTendencias() {
        if (!validarAutenticacao()) return;
        
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  📈 TENDÊNCIAS DE POLUIÇÃO                                   │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        long[] periodo = perguntarPeriodo();
        
        try {
            System.out.println("🔄 Consultando...");
            String resposta = client.consultarTendencias(periodo[0], periodo[1]);
            exibirRelatorioCompleto(resposta, "TENDÊNCIAS DE POLUIÇÃO");
        } catch (Exception e) {
            tratarErroConsulta(e);
        }
    }

    private void consultarMicroclima() {
        if (!validarAutenticacao()) return;
        
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  🌡️  ANÁLISE DE MICROCLIMA                                   │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        String localizacao = perguntarLocalizacao();
        long[] periodo = perguntarPeriodo();
        
        try {
            System.out.println("🔄 Consultando...");
            String resposta = client.consultarMicroclima(localizacao, periodo[0], periodo[1]);
            exibirRelatorioCompleto(resposta, "MICROCLIMA - " + localizacao);
        } catch (Exception e) {
            tratarErroConsulta(e);
        }
    }

    private void consultarEnchentes() {
        if (!validarAutenticacao()) return;
        
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  🌊 ALERTAS DE ENCHENTE                                     │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        String localizacao = perguntarLocalizacao();
        
        try {
            System.out.println("🔄 Consultando (últimas 24h)...");
            String resposta = client.consultarEnchentes(localizacao);
            exibirRelatorioCompleto(resposta, "ENCHENTES - " + localizacao);
        } catch (Exception e) {
            tratarErroConsulta(e);
        }
    }

    private void consultarTrafego() {
        if (!validarAutenticacao()) return;
        
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  🚦 RECOMENDAÇÕES DE TRÁFEGO                                │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        String localizacao = perguntarLocalizacao();
        long[] periodo = perguntarPeriodo();
        
        try {
            System.out.println("🔄 Consultando...");
            String resposta = client.consultarTrafego(localizacao, periodo[0], periodo[1]);
            exibirRelatorioCompleto(resposta, "TRÁFEGO - " + localizacao);
        } catch (Exception e) {
            tratarErroConsulta(e);
        }
    }

    private void inspecionarToken() {
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  🔍 INSPEÇÃO DO TOKEN JWT                                   │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        if (!client.isAutenticado()) {
            System.out.println("⚠️  Você precisa estar autenticado primeiro (opção 2)!");
            return;
        }
        
        client.inspecionarToken();
    }

    private void mostrarCredenciais() {
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  📋 CREDENCIAIS DISPONÍVEIS                                 │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        System.out.println("  Usuários cadastrados no sistema:\n");

        List<String[]> usuarios = config.getUsuarios();
        for (String[] usuario : usuarios) {
            System.out.println("  👤 " + formatarTexto(usuario[2], 20) +
                             " | Usuário: " + formatarTexto(usuario[0], 10) +
                             " | Senha: " + usuario[1]);
        }
        
        System.out.println("\n  💡 Use qualquer uma dessas credenciais na opção 2 (Login)");
    }
    
    // ═══════════════════════════════════════════════════════════════
    // MÉTODOS AUXILIARES
    // ═══════════════════════════════════════════════════════════════

    private boolean validarAutenticacao() {
        if (!client.isAutenticado()) {
            System.out.println("⚠️  Você precisa estar autenticado primeiro!");
            System.out.println("   1. Descubra o Datacenter (opção 1)");
            System.out.println("   2. Faça login (opção 2)");
            return false;
        }
        return true;
    }

    private long[] perguntarPeriodo() {
        System.out.println("Escolha o período:");
        System.out.println("  1. Última hora");
        System.out.println("  2. Últimas 6 horas");
        System.out.println("  3. Últimas 24 horas");
        System.out.println("  4. Última semana");
        System.out.print("\n➤ Opção: ");
        
        int opcao = lerOpcao();
        long fim = System.currentTimeMillis();
        long inicio;
        
        switch (opcao) {
            case 1:
                inicio = fim - (60 * 60 * 1000L); // 1 hora
                break;
            case 2:
                inicio = fim - (6 * 60 * 60 * 1000L); // 6 horas
                break;
            case 4:
                inicio = fim - (7 * 24 * 60 * 60 * 1000L); // 7 dias
                break;
            case 3:
            default:
                inicio = fim - (24 * 60 * 60 * 1000L); // 24 horas (padrão)
                break;
        }
        
        return new long[]{inicio, fim};
    }

    private String perguntarLocalizacao() {
        System.out.println("Escolha a localização:");

        List<String> localizacoes = config.getLocalizacoes();
        for (int i = 0; i < localizacoes.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + localizacoes.get(i));
        }
        System.out.print("\n➤ Opção: ");

        int opcao = lerOpcao();

        if (opcao >= 1 && opcao <= localizacoes.size()) {
            return localizacoes.get(opcao - 1);
        }

        return localizacoes.get(0); // Padrão
    }

    private void exibirRelatorioIQA(String json) {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                   RELATÓRIO DE QUALIDADE DO AR                    ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝\n");
        
        // Extrair campos principais
        String iqa = ClienteHTTP.extrairCampo(json, "iqa");
        String classificacao = ClienteHTTP.extrairCampo(json, "classificacao");
        
        if (iqa != null && classificacao != null) {
            System.out.println("  📊 IQA:           " + iqa + " (" + classificacao + ")");
        }
        
        // Exibir JSON completo formatado
        System.out.println("\n  📄 Detalhes completos:");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        String[] linhas = json.split(",");
        for (String linha : linhas) {
            System.out.println("  " + linha.trim());
        }
    }

    private void exibirRelatorioCompleto(String json, String titulo) {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  " + formatarTexto(titulo, 64) + " ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝\n");
        
        // Extrair campos principais
        String iqa = ClienteHTTP.extrairCampo(json, "iqa");
        String classificacao = ClienteHTTP.extrairCampo(json, "classificacao");
        String tipo = ClienteHTTP.extrairCampo(json, "tipo");
        
        if (tipo != null) {
            System.out.println("  📋 Tipo:          " + tipo);
        }
        if (iqa != null) {
            System.out.println("  📊 IQA:           " + iqa + (classificacao != null ? " (" + classificacao + ")" : ""));
        }
        
        // Exibir JSON formatado
        System.out.println("\n  📄 Dados completos:");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        
        // Formatação básica do JSON
        String jsonFormatado = json
            .replace("{", "{\n  ")
            .replace("}", "\n}")
            .replace(",", ",\n  ");
        
        System.out.println("  " + jsonFormatado.replace("\n", "\n  "));
    }

    private void tratarErroConsulta(Exception e) {
        System.err.println("\n❌ Erro ao consultar relatório: " + e.getMessage());
        
        if (e.getMessage() != null) {
            if (e.getMessage().contains("404") || e.getMessage().contains("não encontrado")) {
                System.out.println("💡 Não há dados disponíveis para este período/localização.");
                System.out.println("   Tente um período maior ou aguarde mais dados dos sensores.");
            } else if (e.getMessage().contains("401") || e.getMessage().contains("Token")) {
                System.out.println("💡 Token expirado ou inválido. Faça login novamente (opção 2).");
            }
        }
    }

    private String formatarTexto(String texto, int largura) {
        if (texto.length() >= largura) {
            return texto.substring(0, largura);
        }
        return String.format("%-" + largura + "s", texto);
    }

    private void pausar() {
        System.out.print("\n[Pressione ENTER para continuar]");
        scanner.nextLine();
    }

    private void limparCache() {
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  🗑️  LIMPAR CACHE DE TOKEN                                  │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");

        System.out.print("⚠️  Confirma limpeza do cache? (s/n): ");
        String confirmacao = scanner.nextLine().trim().toLowerCase();

        if (confirmacao.equals("s") || confirmacao.equals("sim")) {
            client.limparCache();
            System.out.println("\n✅ Cache limpo com sucesso!");
            System.out.println("💡 No próximo login, um novo token será obtido do servidor.");
        } else {
            System.out.println("\n❌ Operação cancelada.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        ClienteApp app = new ClienteApp();
        app.iniciar();
    }
}
