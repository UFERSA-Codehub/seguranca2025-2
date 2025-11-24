package com.project.datacenter;

import com.project.discovery.ClienteDiscovery;
import com.project.security.KeyManager;
import java.net.InetAddress;

public class DatacenterApp {
    
    private static final int PORTA_TCP = 8080;      // Porta para receber dados dos Edge Servers
    private static final int PORTA_HTTP = 9090;     // Porta para API REST (clientes)
    private static final String CAMINHO_DB = "datacenter.db";
    
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║          DATACENTER - Sistema de Monitoramento            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        try {
            // 1. Verificar disponibilidade de chaves RSA
            System.out.println("[DatacenterApp] 🔑 Verificando KeyManager...");
            KeyManager.getRSALocal(); // Inicializa as chaves RSA
            System.out.println("[DatacenterApp] ✅ KeyManager pronto\n");
            
            // 2. Inicializar Banco de Dados
            System.out.println("[DatacenterApp] 💾 Inicializando Banco de Dados...");
            BancoDados bancoDados = new BancoDados(CAMINHO_DB);
            bancoDados.inicializar();
            System.out.println("[DatacenterApp] ✅ Banco de dados pronto\n");
            
            // 3. Iniciar Servidor TCP (recebe dados dos Edge Servers)
            System.out.println("[DatacenterApp] 🌐 Iniciando Servidor TCP na porta " + PORTA_TCP + "...");
            ServidorTCP servidorTCP = new ServidorTCP(PORTA_TCP, bancoDados);
            Thread threadTCP = new Thread(() -> {
                try {
                    servidorTCP.iniciar();
                } catch (Exception e) {
                    System.err.println("[DatacenterApp] ❌ Erro no Servidor TCP: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            threadTCP.setName("ServidorTCP-Thread");
            threadTCP.start();
            System.out.println("[DatacenterApp] ✅ Servidor TCP iniciado\n");
            
            // 4. Iniciar Servidor HTTP (API REST para clientes)
            System.out.println("[DatacenterApp] 🌐 Iniciando Servidor HTTP na porta " + PORTA_HTTP + "...");
            ServidorHTTP servidorHTTP = new ServidorHTTP(PORTA_HTTP, bancoDados);
            Thread threadHTTP = new Thread(() -> {
                try {
                    servidorHTTP.iniciar();
                } catch (Exception e) {
                    System.err.println("[DatacenterApp] ❌ Erro no Servidor HTTP: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            threadHTTP.setName("ServidorHTTP-Thread");
            threadHTTP.start();
            System.out.println("[DatacenterApp] ✅ Servidor HTTP iniciado\n");
            
            // 5. Registrar no Servidor de Localização (Discovery Service)
            System.out.println("[DatacenterApp] 📝 Registrando no Servidor de Localização...");
            try {
                ClienteDiscovery clienteDiscovery = new ClienteDiscovery("127.0.0.1", 4000);
                String meuHost = InetAddress.getLocalHost().getHostAddress();
                boolean registrado = clienteDiscovery.registrarDatacenter(meuHost, PORTA_TCP);
                
                if (registrado) {
                    System.out.println("[DatacenterApp] ✅ Registrado no Servidor de Localização: " + meuHost + ":" + PORTA_TCP);
                } else {
                    System.err.println("[DatacenterApp] ⚠️  Falha ao registrar no Servidor de Localização (continuando mesmo assim...)");
                }
            } catch (Exception e) {
                System.err.println("[DatacenterApp] ⚠️  Erro ao registrar: " + e.getMessage());
            }
            System.out.println();
            
            // 6. Exibir resumo
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║                   DATACENTER ONLINE                       ║");
            System.out.println("╠═══════════════════════════════════════════════════════════╣");
            System.out.println("║  📡 TCP Server:  localhost:" + PORTA_TCP + " (Edge Servers)        ║");
            System.out.println("║  🌐 HTTP API:    localhost:" + PORTA_HTTP + " (Clientes)           ║");
            System.out.println("║  💾 Database:    " + CAMINHO_DB + "                      ║");
            System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
            
            // 7. Monitoramento periódico
            System.out.println("[DatacenterApp] 📊 Iniciando monitoramento...\n");
            executarMonitoramento(bancoDados);
            
            // 8. Shutdown hook para limpeza
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[DatacenterApp] 🛑 Desligando Datacenter...");
                servidorTCP.parar();
                servidorHTTP.parar();
                bancoDados.fechar();
                System.out.println("[DatacenterApp] ✅ Datacenter desligado com sucesso");
            }));
            
        } catch (Exception e) {
            System.err.println("[DatacenterApp] ❌ Erro fatal ao iniciar Datacenter: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void executarMonitoramento(BancoDados bancoDados) {
        Thread monitorThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(60_000); // A cada 60 segundos
                    
                    int totalLeituras = bancoDados.contarLeituras();
                    int totalSensores = bancoDados.contarPorSensor();
                    
                    System.out.println("╔════════════════════════════════════════════════════════╗");
                    System.out.println("║              STATUS DO DATACENTER                      ║");
                    System.out.println("╠════════════════════════════════════════════════════════╣");
                    System.out.printf("║  📊 Total de Leituras:  %-30d  ║%n", totalLeituras);
                    System.out.printf("║  📡 Sensores Ativos:    %-30d  ║%n", totalSensores);
                    System.out.printf("║  ⏰ Timestamp:          %-30d  ║%n", System.currentTimeMillis());
                    System.out.println("╚════════════════════════════════════════════════════════╝\n");
                }
            } catch (InterruptedException e) {
                System.out.println("[DatacenterApp] Monitoramento interrompido");
            } catch (Exception e) {
                System.err.println("[DatacenterApp] ❌ Erro no monitoramento: " + e.getMessage());
                e.printStackTrace();
            }
        });
        monitorThread.setName("MonitorThread");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
}
