package com.project.discovery;

public class InfoServico {
    private String tipo;          // "EDGE" ou "DATACENTER"
    private String host;          // IP ou hostname
    private int porta;            // Porta do serviço
    private long timestampRegistro;  // Quando foi registrado
    private long timestampHeartbeat; // Último heartbeat recebido
    
    public InfoServico(String tipo, String host, int porta) {
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
    
    // Getters
    public String getTipo() { return tipo; }
    public String getHost() { return host; }
    public int getPorta() { return porta; }
    public long getTimestampRegistro() { return timestampRegistro; }
    public long getTimestampHeartbeat() { return timestampHeartbeat; }
    
    @Override
    public String toString() {
        return String.format("InfoServico{tipo='%s', host='%s', porta=%d, registrado=%d, heartbeat=%d}",
            tipo, host, porta, timestampRegistro, timestampHeartbeat);
    }
}
