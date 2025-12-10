package com.project.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


public class RSA {
    private static final Logger logger = LoggerFactory.getLogger("Crypto.RSA");
    private static final String ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORM = "RSA/ECB/PKCS1Padding";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int KEY_SIZE = 2048;

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        logger.debug("Gerando par de chaves RSA...");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
        keyGen.initialize(KEY_SIZE);
        KeyPair keyPair = keyGen.generateKeyPair();

        logger.debug("Par de Chaves RSA gerados ({} bits)", KEY_SIZE);

        return keyPair;
    }

    public static byte[] encrypt(byte[] data, PublicKey publicKey) throws GeneralSecurityException {
        logger.debug("Cifrando {} bytes com RSA...", data.length);
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedData = cipher.doFinal(data);

        logger.debug("Dado cifrado: {} bytes -> {} bytes (Base64: {})",
                data.length,
                encryptedData.length,
                Base64.getEncoder().encodeToString(encryptedData)
        );

        return encryptedData;
    }

    public static byte[] decrypt(byte[] data, PrivateKey privateKey) throws GeneralSecurityException {
        logger.debug("Decifrando {} bytes com RSA", data.length);
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedData = cipher.doFinal(data);

        logger.debug("Dado decifrado: {} bytes -> {} bytes (conteúdo: {})",
                data.length,
                decryptedData.length,
                new String(decryptedData)
        );

        return decryptedData;
    }

    public static byte[] sign(byte[] data, PrivateKey privateKey) throws GeneralSecurityException {
        logger.debug("Assinando {} bytes...", data.length);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        byte[] digitalSignature = signature.sign();

        logger.debug("Assinatura gerada: {} bytes (Base64: {})",
                digitalSignature.length,
                Base64.getEncoder().encodeToString(digitalSignature)
        );  

        return digitalSignature;

    }

    public static boolean verify(byte[] data, byte[] signatureBytes, PublicKey publicKey) throws GeneralSecurityException {
        logger.debug("Verificando assinatura de {} bytes...", data.length);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(data);
        boolean isVerified = signature.verify(signatureBytes);

        logger.debug("Resultado da Verificação: {}", isVerified ? "VÁLIDA ✓" : "INVÁLIDA ✗");

        return isVerified;
    } 

    public static String publicKeyToBase64(PublicKey publicKey) {
        logger.debug("Convertendo chave pública RSA para Base64...");
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        logger.debug("Chave Pública em Base64: {} chars", publicKeyBase64.length());

        return publicKeyBase64;
    }

    public static PublicKey base64ToPublicKey(String base64PublicKey) throws GeneralSecurityException {
        logger.debug("Convertendo Base64 ({} chars) para PublicKey...", base64PublicKey.length());
        byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PublicKey publicKey = keyFactory.generatePublic(spec);

        logger.debug("Chave Pública RSA gerada a partir do Base64 com sucesso.");

        return publicKey;
    }
}
