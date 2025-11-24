package com.project.server.config;

/**
 * Configuração do Discovery Server.
 */
public class DiscoveryConfig extends ServerConfig {
    
    private boolean daemonMode;
    private int timeoutHeartbeat;
    private int tamanhoBuffer;
    
    public DiscoveryConfig() {
        setNome("DiscoveryServer");
        setPorta(4000);
        setDiscoveryEnabled(false); // Discovery não se registra em si mesmo
        this.daemonMode = false;
        this.timeoutHeartbeat = 60000; // 60 segundos
        this.tamanhoBuffer = 1024;
    }
    
    // Getters e Setters
    
    public boolean isDaemonMode() {
        return daemonMode;
    }
    
    public void setDaemonMode(boolean daemonMode) {
        this.daemonMode = daemonMode;
    }
    
    public int getTimeoutHeartbeat() {
        return timeoutHeartbeat;
    }
    
    public void setTimeoutHeartbeat(int timeoutHeartbeat) {
        this.timeoutHeartbeat = timeoutHeartbeat;
    }
    
    public int getTamanhoBuffer() {
        return tamanhoBuffer;
    }
    
    public void setTamanhoBuffer(int tamanhoBuffer) {
        this.tamanhoBuffer = tamanhoBuffer;
    }
}
