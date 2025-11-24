package com.project.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class RetryHelper {

    private static final Logger logger = LoggerFactory.getLogger(RetryHelper.class);

    private final int maxAttempts;
    private final long initialDelayMs;
    private final boolean exponentialBackoff;

    public RetryHelper(int maxAttempts, long initialDelayMs, boolean exponentialBackoff) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.exponentialBackoff = exponentialBackoff;
    }

    public <T> T execute(Supplier<T> operation, String operationName) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();

            } catch (Exception e) {
                lastException = e;

                if (attempt < maxAttempts) {
                    long delay = calculateDelay(attempt);

                    logger.warn("{} falhou (tentativa {}/{}): {}",
                        operationName, attempt, maxAttempts, e.getMessage());
                    logger.info("Tentando novamente em {}ms...", delay);

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Retry interrompido", ie);
                    }
                } else {
                    System.err.println(String.format(
                        "[RetryHelper] ❌ %s falhou após %d tentativas",
                        operationName, maxAttempts
                    ));
                }
            }
        }

        throw new Exception(
            String.format("Operação '%s' falhou após %d tentativas", operationName, maxAttempts),
            lastException
        );
    }

    private long calculateDelay(int attempt) {
        if (!exponentialBackoff) {
            return initialDelayMs;
        }

        // Exponential backoff: delay * (2 ^ (attempt - 1))
        return initialDelayMs * (1L << (attempt - 1));
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }
}