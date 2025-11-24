package com.project.server.datacenter;

import com.project.security.KeyManager;
import com.project.server.AbstractServer;
import com.project.server.config.DatacenterConfig;

/**
 * Servidor Datacenter - Componente central do sistema.
 * Gerencia ServidorTCP (recebe dados de Edge Servers) e ServidorHTTP (API REST para clientes).
 */
public class DatacenterServer extends AbstractServer {
    
    private ServidorTCP servidorTCP;
    private ServidorHTTP servidorHTTP;
    private BancoDados bancoDados;
    private DatacenterConfig config;
    private Thread threadTCP;
    private Thread threadHTTP;
    private Thread threadMonitoramento;
    
    public DatacenterServer(DatacenterConfig config) {
        super(config.getNome(), config.getPortaTCP());
        this.config = config;
    }
    
    @Override
    public void iniciar() throws Exception {
        if (executando) {
            System.err.println("[" + nome + "] Servidor já está em execução!");
            return;
        }
        
        exibirBanner("DATACENTER - Sistema de Monitoramento");
        inicializarComum();
        
        try {
            // 1. Verificar disponibilidade de chaves RSA
            System.out.println("[" + nome + "] 🔑 Verificando KeyManager...");
            KeyManager.getRSALocal(); // Inicializa as chaves RSA
            System.out.println("[" + nome + "] ✅ KeyManager pronto\n");
            
            // 2. Inicializar Banco de Dados
            System.out.println("[" + nome + "] 💾 Inicializando Banco de Dados...");
            bancoDados = new BancoDados(config.getCaminhoDB());
            bancoDados.inicializar();
            System.out.println("[" + nome + "] ✅ Banco de dados pronto\n");
            
            // 3. Iniciar Servidor TCP (recebe dados dos Edge Servers)
            System.out.println("[" + nome + "] 🌐 Iniciando Servidor TCP na porta " + config.getPortaTCP() + "...");
            servidorTCP = new ServidorTCP(config.getPortaTCP(), bancoDados);
            threadTCP = new Thread(() -> {
                try {
                    servidorTCP.iniciar();
                } catch (Exception e) {
                    System.err.println("[" + nome + "] ❌ Erro no Servidor TCP: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            threadTCP.setName("ServidorTCP-Thread");
            threadTCP.start();
            System.out.println("[" + nome + "] ✅ Servidor TCP iniciado\n");
            
            // 4. Iniciar Servidor HTTP (API REST para clientes)
            System.out.println("[" + nome + "] 🌐 Iniciando Servidor HTTP na porta " + config.getPortaHTTP() + "...");
            servidorHTTP = new ServidorHTTP(config.getPortaHTTP(), bancoDados);
            threadHTTP = new Thread(() -> {
                try {
                    servidorHTTP.iniciar();
                } catch (Exception e) {
                    System.err.println("[" + nome + "] ❌ Erro no Servidor HTTP: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            threadHTTP.setName("ServidorHTTP-Thread");
            threadHTTP.start();
            System.out.println("[" + nome + "] ✅ Servidor HTTP iniciado\n");
            
            // 5. Registrar no Discovery Service
            if (config.isDiscoveryEnabled()) {
                registrarNoDiscovery();
            }
            
            // 6. Exibir resumo
            exibirResumoInicial();
            
            // 7. Monitoramento periódico
            System.out.println("[" + nome + "] 📊 Iniciando monitoramento...\n");
            iniciarMonitoramento();
            
            executando = true;
            
        } catch (Exception e) {
            System.err.println("[" + nome + "] ❌ Erro fatal ao iniciar: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Override
    public void parar() {
        if (!executando) {
            return;
        }
        
        System.out.println("\n[" + nome + "] 🛑 Parando servidor...");
        executando = false;
        
        // Parar servidores
        if (servidorTCP != null) {
            servidorTCP.parar();
        }
        if (servidorHTTP != null) {
            servidorHTTP.parar();
        }
        
        // Parar thread de monitoramento
        if (threadMonitoramento != null) {
            threadMonitoramento.interrupt();
        }
        
        // Aguardar threads
        try {
            if (threadTCP != null) threadTCP.join(2000);
            if (threadHTTP != null) threadHTTP.join(2000);
            if (threadMonitoramento != null) threadMonitoramento.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Fechar banco
        if (bancoDados != null) {
            bancoDados.fechar();
        }
        
        System.out.println("[" + nome + "] ✅ Servidor parado");
    }
    
    @Override
    protected boolean registrarNoDiscovery() {
        return autoRegistrarNoDiscovery("DATACENTER");
    }
    
    @Override
    public void exibirStatus() {
        int totalLeituras = bancoDados != null ? bancoDados.contarLeituras() : 0;
        int totalSensores = bancoDados != null ? bancoDados.contarPorSensor() : 0;
        
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║              STATUS DO DATACENTER                      ║");
        System.out.println("╠════════════════════════════════════════════════════════╣");
        System.out.printf("║  📊 Total de Leituras:  %-30d ║%n", totalLeituras);
        System.out.printf("║  📡 Sensores Ativos:    %-30d ║%n", totalSensores);
        System.out.printf("║  ⏰ Timestamp:          %-30d ║%n", System.currentTimeMillis());
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
    }
    
    private void exibirResumoInicial() {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                   DATACENTER ONLINE                       ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║  📡 TCP Server:  localhost:" + config.getPortaTCP() + " (Edge Servers)        ║");
        System.out.println("║  🌐 HTTP API:    localhost:" + config.getPortaHTTP() + " (Clientes)           ║");
        System.out.println("║  💾 Database:    " + config.getCaminhoDB() + "                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
    }
    
    private void iniciarMonitoramento() {
        threadMonitoramento = new Thread(() -> {
            try {
                while (executando) {
                    Thread.sleep(config.getIntervaloMonitoramento());
                    exibirStatus();
                }
            } catch (InterruptedException e) {
                // Thread interrompida, sair silenciosamente
            } catch (Exception e) {
                System.err.println("[" + nome + "] ❌ Erro no monitoramento: " + e.getMessage());
                e.printStackTrace();
            }
        });
        threadMonitoramento.setName("MonitorThread");
        threadMonitoramento.setDaemon(true);
        threadMonitoramento.start();
    }
    
    // Getters
    public BancoDados getBancoDados() {
        return bancoDados;
    }
    
    public ServidorTCP getServidorTCP() {
        return servidorTCP;
    }
    
    public ServidorHTTP getServidorHTTP() {
        return servidorHTTP;
    }
}
