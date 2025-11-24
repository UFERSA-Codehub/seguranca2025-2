package com.project.server.config;

/**
 * Configuração do Edge Server.
 */
public class EdgeConfig extends ServerConfig {
    
    private String edgeId;
    private int intervaloEnvioBatch;
    private int tamanhoBatch;
    private int capacidadeCache;
    
    public EdgeConfig() {
        setNome("EdgeServer");
        setPorta(5000);
        setDiscoveryEnabled(true);
        this.edgeId = "EDGE_001";
        this.intervaloEnvioBatch = 30000; // 30 segundos
        this.tamanhoBatch = 50;
        this.capacidadeCache = 1000;
    }
    
    // Getters e Setters
    
    public String getEdgeId() {
        return edgeId;
    }
    
    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }
    
    public int getIntervaloEnvioBatch() {
        return intervaloEnvioBatch;
    }
    
    public void setIntervaloEnvioBatch(int intervaloEnvioBatch) {
        this.intervaloEnvioBatch = intervaloEnvioBatch;
    }
    
    public int getTamanhoBatch() {
        return tamanhoBatch;
    }
    
    public void setTamanhoBatch(int tamanhoBatch) {
        this.tamanhoBatch = tamanhoBatch;
    }
    
    public int getCapacidadeCache() {
        return capacidadeCache;
    }
    
    public void setCapacidadeCache(int capacidadeCache) {
        this.capacidadeCache = capacidadeCache;
    }
}
