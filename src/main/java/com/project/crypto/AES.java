package com.project.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;


public class AES {
    private static final Logger logger = LoggerFactory.getLogger(AES.class);
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 16; // 128 bits

    private final SecretKey key;

    public AES(SecretKey key) {
        this.key = key;
    }

    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE); //TODO Testar a diferença de 128 e 256
        SecretKey key = keyGen.generateKey();
        logger.info("Generated AES Key ({} bits)", KEY_SIZE);
        return key;
    }

    public String encrypt(String plainText) throws GeneralSecurityException {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivParams = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
        
        // Passo 1 - Cifrar o texto
        byte[] encryptedText = cipher.doFinal(plainText.getBytes());
        
        // Passo 2 - Concatenar IV e texto cifrado
        byte[] encryptedIVAndText = new byte[IV_SIZE + encryptedText.length];
        System.arraycopy(iv, 0, encryptedIVAndText, 0, IV_SIZE);
        System.arraycopy(encryptedText, 0, encryptedIVAndText, IV_SIZE, encryptedText.length);

        // Passo 3 - Retornar como Base64 o IV + texto cifrado
        return Base64.getEncoder().encodeToString(encryptedIVAndText);
    }

    public String decrypt(String cypherText) throws GeneralSecurityException {
        byte[] encryptedIVAndText = Base64.getDecoder().decode(cypherText);


        // Extrair o IV da mensagem
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(encryptedIVAndText, 0, iv, 0, IV_SIZE);
        IvParameterSpec ivParams = new IvParameterSpec(iv);


        // Extrair o texto cifrado da mensagem
        byte[] encryptedText = new byte[encryptedIVAndText.length - IV_SIZE];
        System.arraycopy(encryptedIVAndText, IV_SIZE, encryptedText, 0, encryptedText.length);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, key, ivParams);
        byte[] decryptedText = cipher.doFinal(encryptedText);

        return new String(decryptedText);
    }

    public SecretKey getKey() {
        return key;
    }

    
}