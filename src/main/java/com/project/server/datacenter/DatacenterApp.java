package com.project.server.datacenter;

import com.project.server.config.DatacenterConfig;
import java.util.Scanner;

/**
 * Aplicação principal do Datacenter
 * 
 * Ponto de entrada para o Datacenter que:
 * - Cria configuração padrão
 * - Inicializa DatacenterServer
 * - Aguarda comandos do usuário (quit) ou roda em modo daemon
 */
public class DatacenterApp {
    
    public static void main(String[] args) {
        DatacenterServer servidor = null;
        
        try {
            // 1. Criar configuração padrão
            DatacenterConfig config = new DatacenterConfig();
            
            // 2. Processar argumentos
            for (String arg : args) {
                if (arg.equals("--daemon")) {
                    config.setDaemonMode(true);
                } else {
                    System.err.println("⚠️  Argumento desconhecido: " + arg);
                    System.err.println("Uso: java DatacenterApp [--daemon]");
                }
            }
            
            // 3. Criar e iniciar servidor
            servidor = new DatacenterServer(config);
            
            // 4. Registrar shutdown hook para parada graceful
            final DatacenterServer servidorFinal = servidor;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[DatacenterApp] 🛑 Recebido sinal de parada...");
                if (servidorFinal != null && servidorFinal.isExecutando()) {
                    servidorFinal.parar();
                }
                System.out.println("[DatacenterApp] ✅ Servidor encerrado com sucesso.");
            }));
            
            servidor.iniciar();
            
            if (!config.isDaemonMode()) {
                // MODO INTERATIVO: Loop de comandos
                System.out.println("\n[DatacenterApp] Digite 'quit' para encerrar o servidor");
                System.out.println("[DatacenterApp] Digite 'status' para ver estatísticas\n");
                
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    System.out.print("> ");
                    String comando = scanner.nextLine().trim().toLowerCase();
                    
                    if (comando.equals("quit")) {
                        System.out.println("\n[DatacenterApp] 🛑 Encerrando servidor...");
                        break;
                    } else if (comando.equals("status")) {
                        servidor.exibirStatus();
                    } else if (!comando.isEmpty()) {
                        System.out.println("[DatacenterApp] ⚠️  Comando desconhecido: " + comando);
                        System.out.println("[DatacenterApp] Comandos disponíveis: status, quit");
                    }
                }
                scanner.close();
                
            } else {
                // MODO DAEMON: apenas mantém o servidor rodando
                System.out.println("[DatacenterApp] 🚀 Rodando em modo daemon...");
                System.out.println("[DatacenterApp] Pressione Ctrl+C para parar\n");
                
                // Manter aplicação rodando até shutdown hook
                while (servidor.isExecutando()) {
                    Thread.sleep(1000);
                }
            }
            
        } catch (Exception e) {
            System.err.println("[DatacenterApp] ❌ Erro fatal: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // 5. Limpeza
            if (servidor != null && servidor.isExecutando()) {
                servidor.parar();
            }
            System.out.println("[DatacenterApp] ✅ Aplicação encerrada");
        }
    }
}
