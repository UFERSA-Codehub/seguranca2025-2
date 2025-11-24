package com.project.server.config;

/**
 * Configuração do Datacenter Server.
 */
public class DatacenterConfig extends ServerConfig {
    
    private boolean daemonMode;
    private int portaTCP;
    private int portaHTTP;
    private String caminhoDB;
    private int intervaloMonitoramento;
    
    public DatacenterConfig() {
        setNome("Datacenter");
        setPorta(8080); // Porta TCP primária
        setDiscoveryEnabled(true);
        this.daemonMode = false;
        this.portaTCP = 8080;
        this.portaHTTP = 9090;
        this.caminhoDB = "datacenter.db";
        this.intervaloMonitoramento = 60000; // 60 segundos
    }
    
    // Getters e Setters
    
    public boolean isDaemonMode() {
        return daemonMode;
    }
    
    public void setDaemonMode(boolean daemonMode) {
        this.daemonMode = daemonMode;
    }
    
    public int getPortaTCP() {
        return portaTCP;
    }
    
    public void setPortaTCP(int portaTCP) {
        this.portaTCP = portaTCP;
        setPorta(portaTCP); // Sincronizar com porta base
    }
    
    public int getPortaHTTP() {
        return portaHTTP;
    }
    
    public void setPortaHTTP(int portaHTTP) {
        this.portaHTTP = portaHTTP;
    }
    
    public String getCaminhoDB() {
        return caminhoDB;
    }
    
    public void setCaminhoDB(String caminhoDB) {
        this.caminhoDB = caminhoDB;
    }
    
    public int getIntervaloMonitoramento() {
        return intervaloMonitoramento;
    }
    
    public void setIntervaloMonitoramento(int intervaloMonitoramento) {
        this.intervaloMonitoramento = intervaloMonitoramento;
    }
}
