package com.project.crypto;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HMAC {
    private static final Logger logger = LoggerFactory.getLogger("Crypto.HMAC");
    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_SIZE = 256;

    private final SecretKey key;

    public HMAC(SecretKey key) {
        this.key = key;
        logger.debug("Instância HMAC criada com chave fornecida.");
    }

    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        logger.debug("Gerando chave HMAC...");
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE);
        SecretKey key = keyGen.generateKey();
        logger.debug("Chave HMAC gerada ({} bits)", KEY_SIZE);
        return key;
    }

    public String sign(String data) throws GeneralSecurityException {
        logger.debug("Gerando HMAC para dados ({} chars)...", data.length());
        
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(key);
        byte[] hmacBytes = mac.doFinal(data.getBytes());
        String hmacBase64 = Base64.getEncoder().encodeToString(hmacBytes);
        
        logger.debug("HMAC gerado: {} chars", hmacBase64.length());
        return hmacBase64;
    }

    public boolean verify(String data, String hmacBase64) throws GeneralSecurityException {
        logger.debug("Verificando HMAC para dados ({} chars)...", data.length());
        
        String computedHmac = sign(data);
        boolean isValid = constantTimeEquals(computedHmac, hmacBase64);
        
        logger.debug("Resultado da verificação HMAC: {}", isValid ? "VÁLIDO ✓" : "INVÁLIDO ✗");
        return isValid;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
