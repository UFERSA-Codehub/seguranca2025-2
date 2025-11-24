package com.project.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

public class ClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(ClientConfig.class);

    private Properties props;
    private static final String DEFAULT_CONFIG_PATH = "config/client.properties";



    public ClientConfig() {
        this(DEFAULT_CONFIG_PATH);
    }

    public ClientConfig(String configPath) {
        this.props = new Properties();
        carregarConfiguracao(configPath);
    }

    private void carregarConfiguracao(String configPath) {
        try (InputStream is = new FileInputStream(configPath)) {
            props.load(is);
            logger.info("Configuração carregada: {}", configPath);
        } catch (IOException e) {
            logger.error("Arquivo de configuração não encontrado: {}", configPath, e);
            throw new RuntimeException("[ClientConfig] ❌ Arquivo de configuração não encontrado: " + configPath, e);
        }
    }



    public String getDiscoveryHost() {
        return props.getProperty("discovery.host");
    }

    public int getDiscoveryPort() {
        return Integer.parseInt(props.getProperty("discovery.port"));
    }

    public int getHttpConnectTimeout() {
        return Integer.parseInt(props.getProperty("http.connect.timeout"));
    }

    public int getHttpReadTimeout() {
        return Integer.parseInt(props.getProperty("http.read.timeout"));
    }

    public List<String[]> getUsuarios() {
        List<String[]> usuarios = new ArrayList<>();
        int i = 1;
        while (true) {
            String user = props.getProperty("users." + i);
            if (user == null) break;
            usuarios.add(user.split(":"));
            i++;
        }
        return usuarios;
    }

    public List<String> getLocalizacoes() {
        List<String> locs = new ArrayList<>();
        int i = 1;
        while (true) {
            String loc = props.getProperty("locations." + i);
            if (loc == null) break;
            locs.add(loc);
            i++;
        }
        return locs;
    }

    // Retry configuration
    public boolean isRetryEnabled() {
        return Boolean.parseBoolean(props.getProperty("retry.enabled"));
    }

    public int getRetryMaxAttempts() {
        return Integer.parseInt(props.getProperty("retry.max.attempts"));
    }

    public long getRetryInitialDelayMs() {
        return Long.parseLong(props.getProperty("retry.initial.delay.ms"));
    }

    public boolean isRetryExponentialBackoff() {
        return Boolean.parseBoolean(props.getProperty("retry.exponential.backoff"));
    }

    // Cache configuration
    public boolean isCacheEnabled() {
        return Boolean.parseBoolean(props.getProperty("cache.enabled"));
    }

    public int getCacheTokenTtlHours() {
        return Integer.parseInt(props.getProperty("cache.token.ttl.hours"));
    }

    public String getCacheDirectory() {
        return props.getProperty("cache.directory");
    }

    // HTTPS configuration
    public boolean isHttpsEnabled() {
        return Boolean.parseBoolean(props.getProperty("https.enabled"));
    }

    public boolean isHttpsVerifyHostname() {
        return Boolean.parseBoolean(props.getProperty("https.verify.hostname"));
    }

    public String getHttpsTruststorePath() {
        return props.getProperty("https.truststore.path");
    }

    public String getHttpsTruststorePassword() {
        return props.getProperty("https.truststore.password");
    }
}