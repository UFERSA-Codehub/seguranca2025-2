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
    private static final Logger logger = LoggerFactory.getLogger("Firewall.RuleEngine");

    // Rate limit: max conexoes por segundo por IP:porta
    private static final int MAX_CONNECTIONS_PER_SECOND = 5;
    private static final int MAX_DISCOVERY_CONNECTIONS_PER_SECOND = 30;
    private static final long RATE_LIMIT_WINDOW_MS = 1000;

    // Discovery UDP port
    private static final int DISCOVERY_PORT = 3040;
    private static final int PORT_SCAN_THRESHOLD = 5;
    private static final long PORT_SCAN_WINDOW_MS = 5000;

    // Regras de filtro: politica "negar tudo exceto o permitido"
    // Ordem importa: primeira regra que match eh aplicada
    private static final List<FilterRule> FILTER_RULES = List.of(
        new FilterRule(3000, true, "AuthServer"),
        new FilterRule(3010, true, "Edge (sensores)"),
        new FilterRule(3020, true, "Datacenter (Edge batches)"),
        new FilterRule(3030, true, "Datacenter (CLI client)"),
        new FilterRule(3040, true, "Discovery (UDP)"),
        new FilterRule(-1, false, "Negar tudo - politica default deny")
    );

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

    public Set<String> getBlacklist() {
        return new HashSet<>(blacklist);
    }

    public void recordConnection(String ip, int port) {
        long now = System.currentTimeMillis();
        String rateKey = ip + ":" + port;

        // Registrar timestamp da conexao (por IP:porta)
        connectionTimestamps.computeIfAbsent(rateKey, k -> new ArrayList<>()).add(now);

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

        // Higher limit for Discovery port (many services register at startup)
        int maxConnections = (port == DISCOVERY_PORT) ? MAX_DISCOVERY_CONNECTIONS_PER_SECOND : MAX_CONNECTIONS_PER_SECOND;

        if (recentCount > maxConnections) {
            logger.warn("Rate limit excedido para {}:{} - {} conexoes/seg (limite: {})", ip, port, recentCount, maxConnections);
            return true;
        }

        return false;
    }

    public boolean isPortScanning(String ip, int port) {
        long now = System.currentTimeMillis();
        Long lastAccess = portAccessTimes.get(ip);

        // Se passou da janela de tempo, resetar contagem de portas
        if (lastAccess == null || (now - lastAccess) > PORT_SCAN_WINDOW_MS) {
            portsAccessed.put(ip, ConcurrentHashMap.newKeySet());
        }

        // Registrar porta acessada e timestamp
        portsAccessed.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(port);
        portAccessTimes.put(ip, now);

        // Verificar se acessou muitas portas diferentes
        Set<Integer> ports = portsAccessed.get(ip);
        if (ports != null && ports.size() >= PORT_SCAN_THRESHOLD) {
            logger.warn("Port scan detectado de {} - {} portas acessadas em {}ms: {}",
                    ip, ports.size(), PORT_SCAN_WINDOW_MS, ports);
            return true;
        }

        return false;
    }

    public CheckResult checkConnection(String ip, int port) {
        // Passo 1 - Verificar blacklist
        if (isBlacklisted(ip)) {
            return new CheckResult(false, "BLOCKED", "IP na blacklist");
        }

        // Passo 2 - Verificar regra de filtro (politica default deny)
        FilterRule rule = matchRule(port);
        if (!rule.allow()) {
            logger.warn("Conexao negada para porta {} - Regra: {}", port, rule.description());
            return new CheckResult(false, "DENIED", "Regra: " + rule.description());
        }

        // Passo 3 - Verificar port scan (antes de registrar conexao)
        if (isPortScanning(ip, port)) {
            addToBlacklist(ip);
            return new CheckResult(false, "PORT_SCAN", "Port scan detectado");
        }

        // Passo 4 - Registrar conexao
        recordConnection(ip, port);

        // Passo 5 - Verificar rate limit
        if (isRateLimitExceeded(ip, port)) {
            return new CheckResult(false, "RATE_LIMIT", "Limite de conexoes excedido");
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

    /**
     * Busca a primeira regra que faz match com a porta de destino.
     * Se nenhuma regra especifica fizer match, retorna a regra default (destPort = -1).
     */
    public FilterRule matchRule(int destPort) {
        for (FilterRule rule : FILTER_RULES) {
            if (rule.destPort() == -1 || rule.destPort() == destPort) {
                return rule;
            }
        }
        // Nunca deve chegar aqui se a regra default existir
        return new FilterRule(-1, false, "Negar - nenhuma regra match");
    }

    public record CheckResult(boolean allowed, String alertType, String reason) {}

    public record FilterRule(int destPort, boolean allow, String description) {}
}
