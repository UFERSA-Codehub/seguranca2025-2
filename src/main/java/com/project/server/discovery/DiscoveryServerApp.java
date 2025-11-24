package com.project.server.discovery;

import com.project.server.config.DiscoveryConfig;
import java.util.Scanner;

public class DiscoveryServerApp {
    
    public static void main(String[] args) {
        // Criar configuração
        DiscoveryConfig config = new DiscoveryConfig();
        
        // Processar argumentos
        for (String arg : args) {
            if (arg.equals("--daemon")) {
                config.setDaemonMode(true);
            } else {
                try {
                    config.setPorta(Integer.parseInt(arg));
                } catch (NumberFormatException e) {
                    System.err.println("❌ Porta inválida: " + arg);
                    System.err.println("Uso: java DiscoveryServerApp [porta] [--daemon]");
                    System.exit(1);
                }
            }
        }
        
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║       SERVIDOR DE LOCALIZAÇÃO (DISCOVERY SERVICE)              ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Porta:           %-45d║%n", config.getPorta());
        System.out.println("║  Protocolo:       UDP                                          ║");
        System.out.println("║                                                                ║");
        System.out.println("║  Este servidor permite:                                        ║");
        System.out.println("║    • Edge Servers se registrarem                               ║");
        System.out.println("║    • Datacenters se registrarem                                ║");
        System.out.println("║    • Sensores descobrirem qual Edge usar                       ║");
        System.out.println("║    • Clientes descobrirem qual Datacenter usar                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
        
        // Criar e iniciar servidor
        DiscoveryServer servidor = new DiscoveryServer(config);
        
        // Registrar shutdown hook para parada graceful
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[DiscoveryServerApp] 🛑 Recebido sinal de parada...");
            servidor.parar();
            System.out.println("[DiscoveryServerApp] ✅ Servidor encerrado com sucesso.");
        }));
        
        try {
            servidor.iniciar();

            if (!config.isDaemonMode()) {
                System.out.println("╔════════════════════════════════════════════════════════════════╗");
                System.out.println("║  COMANDOS DISPONÍVEIS:                                         ║");
                System.out.println("║    status  - Exibe serviços registrados                        ║");
                System.out.println("║    quit    - Encerra o servidor                                ║");
                System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

                // Loop de comandos (SOMENTE modo interativo)
                Scanner scanner = new Scanner(System.in);
                boolean executando = true;

                while (executando && servidor.isExecutando()) {
                    System.out.print("> ");
                    String comando = scanner.nextLine().trim().toLowerCase();

                    switch (comando) {
                        case "status":
                            servidor.exibirStatus();
                            break;

                        case "quit":
                        case "exit":
                            executando = false;
                            System.out.println("[DiscoveryServerApp] 🛑 Encerrando servidor...");
                            break;

                        case "help":
                        case "?":
                            System.out.println("\nComandos disponíveis:");
                            System.out.println("  status - Exibe serviços registrados");
                            System.out.println("  quit   - Encerra o servidor\n");
                            break;

                        case "":
                            // Linha vazia, ignorar
                            break;

                        default:
                            System.out.println("❌ Comando desconhecido: " + comando);
                            System.out.println("Digite 'help' para ver comandos disponíveis.\n");
                            break;
                    }
                }

                scanner.close();
                servidor.parar();
                System.out.println("\n[DiscoveryServerApp] 👋 Aplicação finalizada.");
            } else {
                // MODO DAEMON: apenas mantém o servidor rodando
                System.out.println("[DiscoveryServerApp] 🚀 Rodando em modo daemon...");
                System.out.println("[DiscoveryServerApp] Pressione Ctrl+C para parar\n");

                // Manter aplicação rodando até shutdown hook
                while (servidor.isExecutando()) {
                    Thread.sleep(1000);
                }
            }

        } catch (Exception e) {
            System.err.println("[DiscoveryServerApp] ❌ Erro fatal: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
