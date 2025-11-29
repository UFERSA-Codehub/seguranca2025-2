package com.project.server;

public class ServiceInfo {
    private final String serviceId;             // Identificador único do serviço
    private final String serviceType;           // Tipo do serviço (e.g., "EDGE", "DATACENTER")
    private final String host;                  // Endereço IP ou hostname do serviço
    private final int port;                     // Porta do serviço
    private final int httpPort;                 // Porta HTTP do serviço (exclusivo do DATACENTER, posso mudar pra uma lista de ports depois talvez)
    private final long registeredAt;            // Timestamp de quando o serviço foi registrado
    private volatile long lastSeen;             // Timestamp do último heartbeat recebido

    public ServiceInfo(String serviceId, String serviceType, String host, int port) {
        this(serviceId, serviceType, host, port, -1);
    }

    public ServiceInfo(String serviceId, String serviceType, String host, int port, int httpPort) {
        this.serviceId = serviceId;
        this.serviceType = serviceType;
        this.host = host;
        this.port = port;
        this.httpPort = httpPort;
        this.registeredAt = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return serviceType + "{" +
                "serviceId='" + serviceId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", registeredAt=" + registeredAt +
                '}';
    }
}
