package com.project.edge;

import com.project.discovery.ClienteDiscovery;
import com.project.discovery.InfoServico;
import java.net.InetAddress;

public class EdgeServerApp {
    
    private static final int EDGE_PORTA_UDP = 5000;
    private static final String EDGE_ID = "EDGE_001";
    
    private static final int INTERVALO_ENVIO_BATCH = 30000;  // 30 segundos (em ms)
    private static final int TAMANHO_BATCH = 50;              // 50 leituras por batch
    
    private static final String SERVIDOR_LOCALIZACAO_HOST = "127.0.0.1";
    private static final int SERVIDOR_LOCALIZACAO_PORTA = 4000;
    
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║          EDGE SERVER - Sistema de Monitoramento           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // 1. Conectar ao Servidor de Localização
        System.out.println("[EdgeServerApp] 🔍 Conectando ao Servidor de Localização...");
        ClienteDiscovery clienteDiscovery = new ClienteDiscovery(
            SERVIDOR_LOCALIZACAO_HOST, 
            SERVIDOR_LOCALIZACAO_PORTA
        );
        
        // 2. Descobrir endereço do Datacenter
        System.out.println("[EdgeServerApp] 🔍 Descobrindo Datacenter...");
        InfoServico infoDatacenter = clienteDiscovery.descobrirDatacenter();
        
        if (infoDatacenter == null) {
            System.err.println("[EdgeServerApp] ❌ ERRO: Não foi possível descobrir o Datacenter!");
            System.err.println("[EdgeServerApp] Certifique-se de que:");
            System.err.println("[EdgeServerApp]   1. O Servidor de Localização está rodando");
            System.err.println("[EdgeServerApp]   2. O Datacenter já se registrou");
            System.exit(1);
        }
        
        String datacenterHost = infoDatacenter.getHost();
        int datacenterPorta = infoDatacenter.getPorta();
        
        System.out.println("[EdgeServerApp] ✅ Datacenter encontrado: " + datacenterHost + ":" + datacenterPorta);
        
        // 3. Criar Edge Server com integração TCP ao Datacenter
        EdgeServer servidor = new EdgeServer(
            EDGE_PORTA_UDP,         // Porta UDP para receber sensores
            datacenterHost,         // Host do Datacenter (descoberto dinamicamente)
            datacenterPorta,        // Porta TCP do Datacenter (descoberta dinamicamente)
            EDGE_ID,                // ID único deste Edge Server
            INTERVALO_ENVIO_BATCH,  // Intervalo para enviar batches (30s)
            TAMANHO_BATCH           // Número de leituras por batch (50)
        );
        
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                 EDGE SERVER CONFIGURADO                   ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║  📡 Edge UDP:     0.0.0.0:" + EDGE_PORTA_UDP + "                           ║");
        System.out.println("║  🆔 Edge ID:      " + EDGE_ID + "                            ║");
        System.out.println("║  🌐 Datacenter:   " + datacenterHost + ":" + datacenterPorta + "                       ║");
        System.out.println("║  ⏱️  Intervalo:    " + (INTERVALO_ENVIO_BATCH / 1000) + "s                                  ║");
        System.out.println("║  📦 Batch Size:   " + TAMANHO_BATCH + " leituras                          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // 4. Registrar-se no Servidor de Localização
        System.out.println("[EdgeServerApp] 📝 Registrando Edge Server no Servidor de Localização...");
        try {
            String meuHost = InetAddress.getLocalHost().getHostAddress();
            boolean registrado = clienteDiscovery.registrarEdge(meuHost, EDGE_PORTA_UDP);
            
            if (registrado) {
                System.out.println("[EdgeServerApp] ✅ Edge Server registrado com sucesso: " + meuHost + ":" + EDGE_PORTA_UDP);
            } else {
                System.err.println("[EdgeServerApp] ⚠️  Falha ao registrar Edge Server (continuando mesmo assim...)");
            }
        } catch (Exception e) {
            System.err.println("[EdgeServerApp] ⚠️  Erro ao obter endereço local: " + e.getMessage());
        }
        
        System.out.println();
        
        // 5. Iniciar servidor
        servidor.iniciar();
        
        // Shutdown hook para limpeza
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[EdgeServerApp] 🛑 Recebido sinal de shutdown...");
            servidor.parar();
            System.out.println("[EdgeServerApp] ✅ Edge Server desligado com sucesso");
        }));
        
        // Manter aplicação rodando
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("[EdgeServerApp] Interrompido");
            servidor.parar();
        }
    }
}