package com.project.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class CryptoProtocol {
    
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final int IV_SIZE = 16;
    private static final int HMAC_SIZE = 32;
    private static final SecureRandom secureRandom = new SecureRandom();

    public static byte[] encryptAES_HMAC(byte[] data, SessionKeys keys) throws Exception {
        if (data == null || keys == null) {
            throw new IllegalArgumentException("Dados e chaves não podem ser nulos");
        }
        
        // 1. Gerar IV aleatório
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);
        
        // 2. Cifrar com AES-256-CBC
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(keys.getAesKey().getEncoded(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] ciphertext = cipher.doFinal(data);
        
        // 3. Calcular HMAC sobre (IV + CIPHERTEXT)
        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec hmacKey = new SecretKeySpec(keys.getHmacKey().getEncoded(), HMAC_ALGORITHM);
        mac.init(hmacKey);
        byte[] hmac = mac.doFinal(payload);
        
        // 4. Montar mensagem final: [IV][HMAC][CIPHERTEXT]
        byte[] result = new byte[iv.length + hmac.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(hmac, 0, result, iv.length, hmac.length);
        System.arraycopy(ciphertext, 0, result, iv.length + hmac.length, ciphertext.length);
        
        return result;
    }

    public static byte[] decryptAES_HMAC(byte[] encryptedData, SessionKeys keys) throws Exception {
        if (encryptedData == null || keys == null) {
            throw new IllegalArgumentException("Dados e chaves não podem ser nulos");
        }
        
        if (encryptedData.length < IV_SIZE + HMAC_SIZE) {
            throw new IllegalArgumentException("Dados cifrados muito curtos");
        }
        
        // 1. Extrair componentes
        byte[] iv = Arrays.copyOfRange(encryptedData, 0, IV_SIZE);
        byte[] hmacReceived = Arrays.copyOfRange(encryptedData, IV_SIZE, IV_SIZE + HMAC_SIZE);
        byte[] ciphertext = Arrays.copyOfRange(encryptedData, IV_SIZE + HMAC_SIZE, encryptedData.length);
        
        // 2. Verificar HMAC
        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec hmacKey = new SecretKeySpec(keys.getHmacKey().getEncoded(), HMAC_ALGORITHM);
        mac.init(hmacKey);
        byte[] hmacCalculated = mac.doFinal(payload);
        
        if (!Arrays.equals(hmacReceived, hmacCalculated)) {
            throw new SecurityException("HMAC inválido - mensagem adulterada ou chave incorreta");
        }
        
        // 3. Decifrar com AES-256-CBC
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(keys.getAesKey().getEncoded(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        
        return plaintext;
    }

    public static String encryptAES_HMAC_String(String data, SessionKeys keys) throws Exception {
        byte[] encrypted = encryptAES_HMAC(data.getBytes(StandardCharsets.UTF_8), keys);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decryptAES_HMAC_String(String encryptedBase64, SessionKeys keys) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
        byte[] decrypted = decryptAES_HMAC(encrypted, keys);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static byte[] encryptRSA(byte[] data, PublicKey publicKey) throws Exception {
        if (data == null || publicKey == null) {
            throw new IllegalArgumentException("Dados e chave pública não podem ser nulos");
        }
        
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    public static String encryptRSA_String(String data, PublicKey publicKey) throws Exception {
        byte[] encrypted = encryptRSA(data.getBytes(StandardCharsets.UTF_8), publicKey);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static byte[] decryptRSA(byte[] encryptedData, PrivateKey privateKey) throws Exception {
        if (encryptedData == null || privateKey == null) {
            throw new IllegalArgumentException("Dados cifrados e chave privada não podem ser nulos");
        }
        
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedData);
    }

    public static String decryptRSA_String(String encryptedBase64, PrivateKey privateKey) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
        byte[] decrypted = decryptRSA(encrypted, privateKey);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);
        return iv;
    }

    public static byte[] calculateHMAC(byte[] data, SecretKey hmacKey) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(hmacKey.getEncoded(), HMAC_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(data);
    }

    public static boolean verifyHMAC(byte[] data, byte[] hmac, SecretKey hmacKey) throws Exception {
        byte[] calculated = calculateHMAC(data, hmacKey);
        return Arrays.equals(hmac, calculated);
    }
}
