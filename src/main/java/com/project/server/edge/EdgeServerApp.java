package com.project.server.edge;

import java.net.InetAddress;

import com.project.discovery.DiscoveryClient;
import com.project.discovery.ServiceInfo;
import com.project.server.config.EdgeConfig;

public class EdgeServerApp {
    
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║          EDGE SERVER - Sistema de Monitoramento           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        EdgeConfig config = new EdgeConfig();
        
        System.out.println("[EdgeServerApp] 🔍 Conectando ao Servidor de Localização...");
        DiscoveryClient clienteDiscovery = new DiscoveryClient(
            config.getDiscoveryHost(), 
            config.getDiscoveryPort()
        );
        
        System.out.println("[EdgeServerApp] 🔍 Descobrindo Datacenter...");
        ServiceInfo infoDatacenter = clienteDiscovery.descobrirDatacenter();
        
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
            config.getPorta(),              // Porta UDP para receber sensores
            datacenterHost,                 // Host do Datacenter (descoberto dinamicamente)
            datacenterPorta,                // Porta TCP do Datacenter (descoberta dinamicamente)
            config.getEdgeId(),             // ID único deste Edge Server
            config.getIntervaloEnvioBatch(), // Intervalo para enviar batches
            config.getTamanhoBatch()        // Número de leituras por batch
        );
        
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                 EDGE SERVER CONFIGURADO                   ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║  📡 Edge UDP:     0.0.0.0:" + config.getPorta() + "                           ║");
        System.out.println("║  🆔 Edge ID:      " + config.getEdgeId() + "                            ║");
        System.out.println("║  🌐 Datacenter:   " + datacenterHost + ":" + datacenterPorta + "                       ║");
        System.out.println("║  ⏱️  Intervalo:    " + (config.getIntervaloEnvioBatch() / 1000) + "s                                  ║");
        System.out.println("║  📦 Batch Size:   " + config.getTamanhoBatch() + " leituras                          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // 4. Registrar-se no Servidor de Localização
        System.out.println("[EdgeServerApp] 📝 Registrando Edge Server no Servidor de Localização...");
        try {
            String meuHost = InetAddress.getLocalHost().getHostAddress();
            boolean registrado = clienteDiscovery.registrarEdge(meuHost, config.getPorta());
            
            if (registrado) {
                System.out.println("[EdgeServerApp] ✅ Edge Server registrado com sucesso: " + meuHost + ":" + config.getPorta());
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