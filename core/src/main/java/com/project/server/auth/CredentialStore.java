package com.project.server.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialStore {
    private static final Logger logger = LoggerFactory.getLogger("Auth.CredentialStore");

    private final Map<String, String> sensorCredentials;
    private final Map<String, String> userCredentials;

    public CredentialStore() {
        this.sensorCredentials = new ConcurrentHashMap<>();
        this.userCredentials = new ConcurrentHashMap<>();
        initTestData();
    }

    private void initTestData() {
        sensorCredentials.put("SENSOR_001", "senha123");
        sensorCredentials.put("SENSOR_002", "senha456");
        sensorCredentials.put("SENSOR_003", "senha789");
        sensorCredentials.put("SENSOR_004", "senha321");
        sensorCredentials.put("MALICIOUS_SENSOR", "sensor123");
        logger.debug("Sensores de teste registrados: {}", sensorCredentials.size());

        userCredentials.put("admin", "admin123");
        userCredentials.put("cliente", "cliente123");
        logger.debug("Usuários de teste registrados: {}", userCredentials.size());
    }

    public boolean validateSensor(String sensorId, String password) {
        String storedPassword = sensorCredentials.get(sensorId);
        if (storedPassword == null) {
            logger.warn("Sensor não encontrado: {}", sensorId);
            return false;
        }
        boolean valid = storedPassword.equals(password);
        if (!valid) {
            logger.warn("Senha inválida para sensor: {}", sensorId);
        }
        return valid;
    }

    public boolean validateUser(String username, String password) {
        String storedPassword = userCredentials.get(username);
        if (storedPassword == null) {
            logger.warn("Usuário não encontrado: {}", username);
            return false;
        }
        boolean valid = storedPassword.equals(password);
        if (!valid) {
            logger.warn("Senha inválida para usuário: {}", username);
        }
        return valid;
    }

    public void registerSensor(String sensorId, String password) {
        sensorCredentials.put(sensorId, password);
        logger.debug("Sensor registrado: {}", sensorId);
    }

    public void registerUser(String username, String password) {
        userCredentials.put(username, password);
        logger.debug("Usuário registrado: {}", username);
    }

    public int getSensorCount() {
        return sensorCredentials.size();
    }

    public int getUserCount() {
        return userCredentials.size();
    }
}
