package com.project.server.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.network.SecureUDPChannel;
import com.project.server.ServiceInfo;

public class ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger("ServiceRegistry");

    // Intervalo de verificação de timeout (30s)
    private static final int TIMEOUT_CHECK_INTERVAL_SECONDS = 30;
    // Tempo máximo sem heartbeat antes de remover serviço (60s)
    private static final long HEARTBEAT_TIMEOUT_MS = 60_000;

    private final Map<String, ServiceInfo> registeredEdges;
    private final Map<String, ServiceInfo> registeredDatacenters;
    private final SecureUDPChannel channel;
    private ScheduledExecutorService scheduler;

    public ServiceRegistry(SecureUDPChannel channel) {
        this.channel = channel;
        this.registeredEdges = new ConcurrentHashMap<>();
        this.registeredDatacenters = new ConcurrentHashMap<>();
    }

    public void startTimeoutChecker() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            this::checkHeartbeatTimeouts,
            TIMEOUT_CHECK_INTERVAL_SECONDS,
            TIMEOUT_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        logger.info("Verificador de heartbeat iniciado - intervalo: {}s, timeout: {}s",
                TIMEOUT_CHECK_INTERVAL_SECONDS, HEARTBEAT_TIMEOUT_MS / 1000);
    }

    public void stopTimeoutChecker() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkHeartbeatTimeouts() {
        long now = System.currentTimeMillis();
        List<String> expiredEdges = new ArrayList<>();
        List<String> expiredDatacenters = new ArrayList<>();

        // Verificar Edges
        for (Map.Entry<String, ServiceInfo> entry : registeredEdges.entrySet()) {
            if (now - entry.getValue().getLastSeen() > HEARTBEAT_TIMEOUT_MS) {
                expiredEdges.add(entry.getKey());
            }
        }

        // Verificar Datacenters
        for (Map.Entry<String, ServiceInfo> entry : registeredDatacenters.entrySet()) {
            if (now - entry.getValue().getLastSeen() > HEARTBEAT_TIMEOUT_MS) {
                expiredDatacenters.add(entry.getKey());
            }
        }

        // Remover Edges expirados
        for (String edgeId : expiredEdges) {
            registeredEdges.remove(edgeId);
            channel.clearPeerSession(edgeId);
            logger.warn("EDGE {} removido por timeout de heartbeat", edgeId);
        }

        // Remover Datacenters expirados
        for (String dcId : expiredDatacenters) {
            registeredDatacenters.remove(dcId);
            channel.clearPeerSession(dcId);
            logger.warn("DATACENTER {} removido por timeout de heartbeat", dcId);
        }
    }

    public void registerEdge(String serviceId, String host, int port) {
        registeredEdges.put(serviceId, new ServiceInfo(serviceId, "EDGE", host, port));
        logger.info("EDGE registrado: {}@{}:{}", serviceId, host, port);
    }

    public void registerDatacenter(String serviceId, String host, int tcpPort, int httpPort) {
        registeredDatacenters.put(serviceId, new ServiceInfo(serviceId, "DATACENTER", host, tcpPort, httpPort));
        logger.info("DATACENTER TCP registrado: {}@{}:{}", serviceId, host, tcpPort);
        logger.info("DATACENTER HTTP registrado: {}@{}:{}", serviceId, host, httpPort);
    }

    public boolean updateEdgeLastSeen(String edgeId) {
        ServiceInfo edge = registeredEdges.get(edgeId);
        if (edge != null) {
            edge.updateLastSeen();
            return true;
        }
        return false;
    }

    public boolean updateDatacenterLastSeen(String dcId) {
        ServiceInfo dc = registeredDatacenters.get(dcId);
        if (dc != null) {
            dc.updateLastSeen();
            return true;
        }
        return false;
    }

    public ServiceInfo getFirstEdge() {
        if (registeredEdges.isEmpty()) {
            return null;
        }
        return registeredEdges.values().iterator().next();
    }

    public ServiceInfo getFirstDatacenter() {
        if (registeredDatacenters.isEmpty()) {
            return null;
        }
        return registeredDatacenters.values().iterator().next();
    }

    public boolean hasEdges() {
        return !registeredEdges.isEmpty();
    }

    public boolean hasDatacenters() {
        return !registeredDatacenters.isEmpty();
    }

    public int getEdgeCount() {
        return registeredEdges.size();
    }

    public int getDatacenterCount() {
        return registeredDatacenters.size();
    }
}
