package com.project.server;

import com.project.discovery.DiscoveryClient;
import com.project.discovery.ServiceInfo;
import java.net.InetAddress;

/**
 * Classe base abstrata para servidores do sistema.
 * Fornece funcionalidades comuns como shutdown hooks e discovery integration.
 */
public abstract class AbstractServer implements Server {
    
    protected volatile boolean executando;
    protected String nome;
    protected int porta;
    protected DiscoveryClient clienteDiscovery;
    
    // Configuração Discovery Service
    protected static final String DISCOVERY_HOST = "127.0.0.1";
    protected static final int DISCOVERY_PORT = 4000;
    
    public AbstractServer(String nome, int porta) {
        this.nome = nome;
        this.porta = porta;
        this.executando = false;
    }
    
    /**
     * Registra shutdown hook automático.
     */
    protected void registrarShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[" + nome + "] 🛑 Recebido sinal de shutdown...");
            parar();
            System.out.println("[" + nome + "] ✅ Desligado com sucesso");
        }));
    }
    
    /**
     * Conecta ao Discovery Service.
     */
    protected void conectarDiscoveryService() {
        try {
            this.clienteDiscovery = new DiscoveryClient(DISCOVERY_HOST, DISCOVERY_PORT);
            System.out.println("[" + nome + "] 🔍 Conectado ao Discovery Service");
        } catch (Exception e) {
            System.err.println("[" + nome + "] ⚠️  Erro ao conectar ao Discovery: " + e.getMessage());
        }
    }
    
    /**
     * Registra o servidor no Discovery Service.
     * @return true se registrado com sucesso, false caso contrário
     */
    protected abstract boolean registrarNoDiscovery();
    
    /**
     * Descobre um serviço no Discovery Service.
     * @param tipoServico tipo do serviço (DATACENTER ou EDGE)
     * @return ServiceInfo com dados do serviço descoberto ou null
     */
    protected ServiceInfo descobrirServico(String tipoServico) {
        if (clienteDiscovery == null) {
            conectarDiscoveryService();
        }
        
        System.out.println("[" + nome + "] 🔍 Descobrindo " + tipoServico + "...");
        
        if ("DATACENTER".equals(tipoServico)) {
            return clienteDiscovery.descobrirDatacenter();
        } else if ("EDGE".equals(tipoServico)) {
            return clienteDiscovery.descobrirEdge();
        }
        
        return null;
    }
    
    /**
     * Registra este servidor no Discovery Service usando seu IP local.
     * @param tipoServico tipo do serviço (DATACENTER ou EDGE)
     * @return true se registrado com sucesso
     */
    protected boolean autoRegistrarNoDiscovery(String tipoServico) {
        if (clienteDiscovery == null) {
            conectarDiscoveryService();
        }
        
        if (clienteDiscovery == null) {
            return false;
        }
        
        try {
            String meuHost = InetAddress.getLocalHost().getHostAddress();
            System.out.println("[" + nome + "] 📝 Registrando no Discovery Service...");
            
            boolean registrado = false;
            if ("DATACENTER".equals(tipoServico)) {
                registrado = clienteDiscovery.registrarDatacenter(meuHost, porta);
            } else if ("EDGE".equals(tipoServico)) {
                registrado = clienteDiscovery.registrarEdge(meuHost, porta);
            }
            
            if (registrado) {
                System.out.println("[" + nome + "] ✅ Registrado: " + meuHost + ":" + porta);
            } else {
                System.err.println("[" + nome + "] ⚠️  Falha ao registrar (continuando mesmo assim...)");
            }
            
            return registrado;
            
        } catch (Exception e) {
            System.err.println("[" + nome + "] ⚠️  Erro ao registrar: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isExecutando() {
        return executando;
    }
    
    @Override
    public String getNome() {
        return nome;
    }
    
    @Override
    public int getPorta() {
        return porta;
    }
    
    /**
     * Template method para inicialização comum.
     */
    protected void inicializarComum() throws Exception {
        System.out.println("\n[" + nome + "] 🚀 Inicializando servidor...");
        registrarShutdownHook();
        conectarDiscoveryService();
    }
    
    /**
     * Exibe banner de inicialização.
     */
    protected void exibirBanner(String titulo) {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  " + centralizarTexto(titulo, 57) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
    }
    
    /**
     * Centraliza texto em uma largura específica.
     */
    private String centralizarTexto(String texto, int largura) {
        if (texto.length() >= largura) {
            return texto.substring(0, largura);
        }
        int padding = (largura - texto.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + texto + " ".repeat(Math.max(0, largura - texto.length() - padding));
    }
}
