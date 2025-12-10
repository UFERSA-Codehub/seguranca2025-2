package com.project.server.firewall;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleEngine {
    private static final Logger logger = LoggerFactory.getLogger("RuleEngine");

    private static final int MAX_CONNECTIONS_PER_SECOND = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 1000;
    private static final int PORT_SCAN_THRESHOLD = 3;
    private static final long PORT_SCAN_WINDOW_MS = 5000;

    private final Set<String> blacklist;
    private final Map<String, List<Long>> connectionTimestamps;
    private final Map<String, Set<Integer>> portsAccessed;
    private final Map<String, Long> portAccessTimes;

    public RuleEngine() {
        this.blacklist = ConcurrentHashMap.newKeySet();
        this.connectionTimestamps = new ConcurrentHashMap<>();
        this.portsAccessed = new ConcurrentHashMap<>();
        this.portAccessTimes = new ConcurrentHashMap<>();
    }

    public boolean isBlacklisted(String ip) {
        return blacklist.contains(ip);
    }

    public void addToBlacklist(String ip) {
        blacklist.add(ip);
        logger.warn("IP adicionado a blacklist: {}", ip);
    }

    public void removeFromBlacklist(String ip) {
        blacklist.remove(ip);
        logger.info("IP removido da blacklist: {}", ip);
    }

    public Set<String> getBlacklist() {
        return new HashSet<>(blacklist);
    }

    public void recordConnection(String ip, int port) {
        long now = System.currentTimeMillis();
        String rateKey = ip + ":" + port;

        // Registrar timestamp da conexao (por servico)
        connectionTimestamps.computeIfAbsent(rateKey, k -> new ArrayList<>()).add(now);

        // Registrar porta acessada
        long lastAccess = portAccessTimes.getOrDefault(ip, 0L);
        if (now - lastAccess > PORT_SCAN_WINDOW_MS) {
            portsAccessed.put(ip, new HashSet<>());
        }
        portsAccessed.computeIfAbsent(ip, k -> new HashSet<>()).add(port);
        portAccessTimes.put(ip, now);

        // Limpar timestamps antigos periodicamente
        cleanOldTimestamps(rateKey);
    }

    public boolean isRateLimitExceeded(String ip, int port) {
        String rateKey = ip + ":" + port;
        long now = System.currentTimeMillis();
        long cutoff = now - RATE_LIMIT_WINDOW_MS;

        List<Long> timestamps = connectionTimestamps.getOrDefault(rateKey, List.of());
        long recentCount = timestamps.stream()
                .filter(t -> t >= cutoff)
                .count();

        if (recentCount > MAX_CONNECTIONS_PER_SECOND) {
            logger.warn("Rate limit excedido para {}:{} - {} conexoes/seg", ip, port, recentCount);
            return true;
        }

        return false;
    }

    public boolean isPortScanning(String ip) {
        Set<Integer> ports = portsAccessed.getOrDefault(ip, Set.of());
        Long lastAccess = portAccessTimes.get(ip);

        if (lastAccess == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastAccess > PORT_SCAN_WINDOW_MS) {
            return false;
        }

        if (ports.size() >= PORT_SCAN_THRESHOLD) {
            logger.warn("Port scan detectado para IP {}: {} portas em {}ms", 
                       ip, ports.size(), PORT_SCAN_WINDOW_MS);
            return true;
        }

        return false;
    }

    public CheckResult checkConnection(String ip, int port) {
        // Passo 1 - Verificar blacklist
        if (isBlacklisted(ip)) {
            return new CheckResult(false, "BLOCKED", "IP na blacklist");
        }

        // Passo 2 - Registrar conexao
        recordConnection(ip, port);

        // Passo 3 - Verificar rate limit
        if (isRateLimitExceeded(ip, port)) {
            return new CheckResult(false, "RATE_LIMIT", "Limite de conexoes excedido");
        }

        // Passo 4 - Verificar port scan
        if (isPortScanning(ip)) {
            addToBlacklist(ip);
            return new CheckResult(false, "PORT_SCAN", "Port scan detectado");
        }

        return new CheckResult(true, null, null);
    }

    private void cleanOldTimestamps(String rateKey) {
        long cutoff = System.currentTimeMillis() - RATE_LIMIT_WINDOW_MS * 10;
        List<Long> timestamps = connectionTimestamps.get(rateKey);
        if (timestamps != null) {
            timestamps.removeIf(t -> t < cutoff);
        }
    }

    public record CheckResult(boolean allowed, String alertType, String reason) {}
}
