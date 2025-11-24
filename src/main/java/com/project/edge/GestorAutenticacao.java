package com.project.edge;

import java.util.HashMap;
import java.util.Map;

public class GestorAutenticacao {
    private Map<String, String> credenciaisValidas;    // sensorId -> token esperado
    private Map<String, Long> tentativasInvalidas;     // sensorId -> timestamp da última tentativa
    private static final long BLOQUEIO_DURACAO = 60000; // 1 minuto de bloqueio após falha

    public GestorAutenticacao() {
        this.credenciaisValidas = new HashMap<>();
        this.tentativasInvalidas = new HashMap<>();
        inicializarCredenciais();
    }

    private void inicializarCredenciais() {
        credenciaisValidas.put("SENSOR_001", "TOKEN_SENSOR_001_8f3a9b2c");
        credenciaisValidas.put("SENSOR_002", "TOKEN_SENSOR_002_7e4d6a1f");
        credenciaisValidas.put("SENSOR_003", "TOKEN_SENSOR_003_5c9b2e8a");
        credenciaisValidas.put("SENSOR_004", "TOKEN_SENSOR_004_3f7a1d9c");
    }

    public boolean autenticar(String sensorId, String credencial) {
        if (sensorId == null || credencial == null) {
            System.err.println("[GestorAutenticacao] SensorId ou credencial nulos");
            return false;
        }

        if (estaBloqueado(sensorId)) {
            System.err.println("[GestorAutenticacao] Sensor " + sensorId + " está temporariamente bloqueado");
            return false;
        }

        if (!credenciaisValidas.containsKey(sensorId)) {
            System.err.println("[GestorAutenticacao] ❌ Sensor desconhecido: " + sensorId);
            registrarTentativaInvalida(sensorId);
            return false;
        }

        String credencialEsperada = credenciaisValidas.get(sensorId);
        
        if (!credencialEsperada.equals(credencial)) {
            System.err.println("[GestorAutenticacao] ❌ Credencial inválida para sensor: " + sensorId);
            registrarTentativaInvalida(sensorId);
            return false;
        }

        tentativasInvalidas.remove(sensorId);
        return true;
    }

    private boolean estaBloqueado(String sensorId) {
        if (!tentativasInvalidas.containsKey(sensorId)) {
            return false;
        }

        long ultimaTentativa = tentativasInvalidas.get(sensorId);
        long agora = System.currentTimeMillis();
        
        if (agora - ultimaTentativa < BLOQUEIO_DURACAO) {
            return true;
        }

        tentativasInvalidas.remove(sensorId);
        return false;
    }

    private void registrarTentativaInvalida(String sensorId) {
        tentativasInvalidas.put(sensorId, System.currentTimeMillis());
    }

    public void registrarSensor(String sensorId, String credencial) {
        credenciaisValidas.put(sensorId, credencial);
        System.out.println("[GestorAutenticacao] Novo sensor registrado: " + sensorId);
    }

    public void removerSensor(String sensorId) {
        credenciaisValidas.remove(sensorId);
        tentativasInvalidas.remove(sensorId);
        System.out.println("[GestorAutenticacao] Sensor removido: " + sensorId);
    }

    public int getTotalSensoresAutorizados() {
        return credenciaisValidas.size();
    }

    public int getTotalSensoresBloqueados() {
        long agora = System.currentTimeMillis();
        return (int) tentativasInvalidas.entrySet().stream()
            .filter(entry -> agora - entry.getValue() < BLOQUEIO_DURACAO)
            .count();
    }
}
