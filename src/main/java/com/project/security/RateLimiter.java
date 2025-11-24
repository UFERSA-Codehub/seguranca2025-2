package com.project.security;

import java.util.HashMap;
import java.util.Map;

public class RateLimiter {
    private final Map<String, Integer> tentativas = new HashMap<>();
    private final Map<String, Long> bloqueios = new HashMap<>();
    private final int maxTentativas;
    private final long duracaoBloqueioMs;

    public RateLimiter(int maxTentativas, long duracaoBloqueioMs) {
        this.maxTentativas = maxTentativas;
        this.duracaoBloqueioMs = duracaoBloqueioMs;
    }

    public boolean estaBloqueado(String id) {
        if (!bloqueios.containsKey(id)) {
            return false;
        }
        
        long tempoBloqueio = bloqueios.get(id);
        long agora = System.currentTimeMillis();
        
        if (agora - tempoBloqueio >= duracaoBloqueioMs) {
            // Bloqueio expirou - remover
            bloqueios.remove(id);
            tentativas.remove(id);
            return false;
        }
        
        return true;
    }

    public boolean registrarTentativaFalha(String id) {
        int count = tentativas.getOrDefault(id, 0) + 1;
        tentativas.put(id, count);
        
        if (count >= maxTentativas) {
            // Atingiu limite - bloquear
            bloqueios.put(id, System.currentTimeMillis());
            tentativas.remove(id);
            return true;  // Foi bloqueado
        }
        
        return false;  // Ainda permitido
    }

    public void resetar(String id) {
        tentativas.remove(id);
        bloqueios.remove(id);
    }

    public int getTentativasRestantes(String id) {
        if (estaBloqueado(id)) {
            return 0;
        }
        int count = tentativas.getOrDefault(id, 0);
        return Math.max(0, maxTentativas - count);
    }

    public long getTempoRestanteBloqueio(String id) {
        if (!bloqueios.containsKey(id)) {
            return 0;
        }
        
        long tempoBloqueio = bloqueios.get(id);
        long agora = System.currentTimeMillis();
        long restante = (tempoBloqueio + duracaoBloqueioMs - agora) / 1000;
        
        return Math.max(0, restante);
    }

    public void limparExpirados() {
        long agora = System.currentTimeMillis();
        bloqueios.entrySet().removeIf(entry -> 
            agora - entry.getValue() >= duracaoBloqueioMs
        );
    }

    public String diagnostico() {
        return String.format("[RateLimiter] Tentativas ativas: %d, Bloqueios ativos: %d, Config: %d tentativas / %dms",
            tentativas.size(), bloqueios.size(), maxTentativas, duracaoBloqueioMs);
    }
}
