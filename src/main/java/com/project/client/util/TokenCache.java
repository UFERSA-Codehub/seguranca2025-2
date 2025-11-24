package com.project.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.Duration;

public class TokenCache {

    private static final Logger logger = LoggerFactory.getLogger(TokenCache.class);

    private final String cacheDirectory;
    private final int ttlHours;
    private static final String TOKEN_FILENAME = "jwt.token";

    public TokenCache(String cacheDirectory, int ttlHours) {
        this.cacheDirectory = cacheDirectory;
        this.ttlHours = ttlHours;
    }

    public void salvar(String token) {
        try {
            Path cacheDir = Paths.get(cacheDirectory);
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }

            Path tokenPath = cacheDir.resolve(TOKEN_FILENAME);
            String data = Instant.now().toString() + "\n" + token;
            Files.writeString(tokenPath, data);

            logger.info("Token salvo em cache");

        } catch (IOException e) {
            logger.warn("Falha ao salvar token: {}", e.getMessage());
        }
    }

    public String carregar() {
        try {
            Path tokenPath = Paths.get(cacheDirectory, TOKEN_FILENAME);
            if (!Files.exists(tokenPath)) {
                logger.info("Cache vazio");
                return null;
            }

            String content = Files.readString(tokenPath);
            String[] lines = content.split("\n", 2);

            if (lines.length < 2) {
                logger.warn("Cache corrompido");
                limpar();
                return null;
            }

            Instant savedAt = Instant.parse(lines[0]);
            Instant now = Instant.now();
            Duration age = Duration.between(savedAt, now);

            if (age.toHours() >= ttlHours) {
                logger.info("Token expirado (idade: {}h, TTL: {}h)", age.toHours(), ttlHours);
                limpar();
                return null;
            }

            long horasRestantes = ttlHours - age.toHours();
            logger.info("Token válido em cache (expira em ~{}h)", horasRestantes);

            return lines[1];

        } catch (Exception e) {
            logger.warn("Erro ao carregar token: {}", e.getMessage());
            limpar();
            return null;
        }
    }

    public void limpar() {
        try {
            Path tokenPath = Paths.get(cacheDirectory, TOKEN_FILENAME);
            if (Files.deleteIfExists(tokenPath)) {
                logger.info("Cache limpo");
            }
        } catch (IOException e) {
            logger.warn("Erro ao limpar cache: {}", e.getMessage());
        }
    }
}