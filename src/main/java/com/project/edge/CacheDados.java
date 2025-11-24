package com.project.edge;

import com.project.model.DadosAmbientais;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CacheDados {
    private Map<String, List<DadosAmbientais>> cache;              // sensorId -> lista de leituras
    private Map<String, Long> ultimaAtualizacao;                   // sensorId -> timestamp
    private int capacidadeMaximaPorSensor;                         // Máximo de leituras por sensor
    private static final long TIMEOUT_SENSOR = 60000;              // 1 minuto sem dados = sensor inativo

    public CacheDados() {
        this(100);
    }

    public CacheDados(int capacidadeMaximaPorSensor) {
        this.cache = new ConcurrentHashMap<>();
        this.ultimaAtualizacao = new ConcurrentHashMap<>();
        this.capacidadeMaximaPorSensor = capacidadeMaximaPorSensor;
    }

    public synchronized void adicionarLeitura(String sensorId, DadosAmbientais dados) {
        if (!cache.containsKey(sensorId)) {
            cache.put(sensorId, new ArrayList<>());
        }

        List<DadosAmbientais> leituras = cache.get(sensorId);
        leituras.add(dados);

        if (leituras.size() > capacidadeMaximaPorSensor) {
            leituras.remove(0);
        }

        ultimaAtualizacao.put(sensorId, System.currentTimeMillis());
    }

    public List<DadosAmbientais> obterLeituras(String sensorId) {
        return cache.getOrDefault(sensorId, new ArrayList<>());
    }

    public List<DadosAmbientais> obterUltimasLeituras(String sensorId, int quantidade) {
        List<DadosAmbientais> todasLeituras = obterLeituras(sensorId);
        int inicio = Math.max(0, todasLeituras.size() - quantidade);
        return new ArrayList<>(todasLeituras.subList(inicio, todasLeituras.size()));
    }

    public DadosAmbientais obterUltimaLeitura(String sensorId) {
        List<DadosAmbientais> leituras = obterLeituras(sensorId);
        return leituras.isEmpty() ? null : leituras.get(leituras.size() - 1);
    }

    public Set<String> obterSensoresAtivos() {
        Set<String> ativos = new HashSet<>();
        long agora = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : ultimaAtualizacao.entrySet()) {
            if (agora - entry.getValue() <= TIMEOUT_SENSOR) {
                ativos.add(entry.getKey());
            }
        }

        return ativos;
    }

    public int getTotalLeituras() {
        return cache.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    public int getTotalLeiturasDoSensor(String sensorId) {
        return obterLeituras(sensorId).size();
    }

    public Set<String> getTodosSensores() {
        return new HashSet<>(cache.keySet());
    }

    public void limparDadosAntigos() {
        long agora = System.currentTimeMillis();
        List<String> sensoresInativos = new ArrayList<>();

        for (Map.Entry<String, Long> entry : ultimaAtualizacao.entrySet()) {
            if (agora - entry.getValue() > TIMEOUT_SENSOR * 10) {
                sensoresInativos.add(entry.getKey());
            }
        }

        for (String sensorId : sensoresInativos) {
            cache.remove(sensorId);
            ultimaAtualizacao.remove(sensorId);
            System.out.println("[CacheDados] Dados antigos do sensor " + sensorId + " removidos");
        }
    }

    public void limpar() {
        cache.clear();
        ultimaAtualizacao.clear();
        System.out.println("[CacheDados] Cache limpo completamente");
    }

    public Map<String, Integer> obterEstatisticas() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total_sensores", cache.size());
        stats.put("total_leituras", getTotalLeituras());
        stats.put("sensores_ativos", obterSensoresAtivos().size());
        return stats;
    }

    @Override
    public String toString() {
        return String.format("CacheDados{sensores=%d, leituras=%d, ativos=%d}",
            cache.size(), getTotalLeituras(), obterSensoresAtivos().size());
    }
}
