package com.project.server.config;

/**
 * Configuração base para todos os servidores.
 */
public abstract class ServerConfig {
    
    private String nome;
    private int porta;
    private boolean discoveryEnabled;
    private String discoveryHost;
    private int discoveryPort;
    
    public ServerConfig() {
        // Valores padrão
        this.discoveryEnabled = true;
        this.discoveryHost = "127.0.0.1";
        this.discoveryPort = 4000;
    }
    
    // Getters e Setters
    
    public String getNome() {
        return nome;
    }
    
    public void setNome(String nome) {
        this.nome = nome;
    }
    
    public int getPorta() {
        return porta;
    }
    
    public void setPorta(int porta) {
        this.porta = porta;
    }
    
    public boolean isDiscoveryEnabled() {
        return discoveryEnabled;
    }
    
    public void setDiscoveryEnabled(boolean discoveryEnabled) {
        this.discoveryEnabled = discoveryEnabled;
    }
    
    public String getDiscoveryHost() {
        return discoveryHost;
    }
    
    public void setDiscoveryHost(String discoveryHost) {
        this.discoveryHost = discoveryHost;
    }
    
    public int getDiscoveryPort() {
        return discoveryPort;
    }
    
    public void setDiscoveryPort(int discoveryPort) {
        this.discoveryPort = discoveryPort;
    }
}
