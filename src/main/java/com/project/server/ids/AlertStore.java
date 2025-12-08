package com.project.server.ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AlertStore {

    private final Map<String, List<Alert>> alertsByIp;
    private final List<Alert> allAlerts;

    public AlertStore() {
        this.alertsByIp = new ConcurrentHashMap<>();
        this.allAlerts = new ArrayList<>();
    }

    public synchronized void store(Alert alert) {
        allAlerts.add(alert);
        alertsByIp.computeIfAbsent(alert.sourceIp(), k -> new ArrayList<>()).add(alert);
    }

    public synchronized List<Alert> getByIp(String ip) {
        return new ArrayList<>(alertsByIp.getOrDefault(ip, List.of()));
    }

    public synchronized List<Alert> getAll() {
        return new ArrayList<>(allAlerts);
    }

    public synchronized int countByIp(String ip, long windowMs) {
        long cutoff = System.currentTimeMillis() - windowMs;
        List<Alert> alerts = alertsByIp.getOrDefault(ip, List.of());
        return (int) alerts.stream()
                .filter(a -> a.timestamp() >= cutoff)
                .count();
    }

    public synchronized int getTotalCount() {
        return allAlerts.size();
    }

    public synchronized List<String> getDistinctIps() {
        return new ArrayList<>(alertsByIp.keySet());
    }

    public synchronized Map<String, Long> getAlertCountByType() {
        return allAlerts.stream()
                .collect(Collectors.groupingBy(Alert::alertType, Collectors.counting()));
    }

    public synchronized void clear() {
        allAlerts.clear();
        alertsByIp.clear();
    }

    public record Alert(
        String sourceIp,
        int sourcePort,
        String destService,
        String alertType,
        String content,
        long timestamp
    ) {
        public static Alert of(String sourceIp, int sourcePort, String destService, 
                               String alertType, String content) {
            return new Alert(sourceIp, sourcePort, destService, alertType, content, 
                           System.currentTimeMillis());
        }
    }
}
