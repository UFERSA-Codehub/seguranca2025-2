package com.project.discovery;

public class ServiceInfo {
    private String tipo;
    private String host;
    private int porta;
    private long timestampRegistro;
    private long timestampHeartbeat;
    
    public ServiceInfo(String tipo, String host, int porta) {
        this.tipo = tipo;
        this.host = host;
        this.porta = porta;
        this.timestampRegistro = System.currentTimeMillis();
        this.timestampHeartbeat = System.currentTimeMillis();
    }
    
    public void atualizarHeartbeat() {
        this.timestampHeartbeat = System.currentTimeMillis();
    }
    
    public boolean estaAtivo(long timeoutMs) {
        return (System.currentTimeMillis() - timestampHeartbeat) < timeoutMs;
    }
    
    public String getTipo() { return tipo; }
    public String getHost() { return host; }
    public int getPorta() { return porta; }
    public long getTimestampRegistro() { return timestampRegistro; }
    public long getTimestampHeartbeat() { return timestampHeartbeat; }
    
    @Override
    public String toString() {
        return String.format("ServiceInfo{tipo='%s', host='%s', porta=%d, registrado=%d, heartbeat=%d}",
            tipo, host, porta, timestampRegistro, timestampHeartbeat);
    }
}
