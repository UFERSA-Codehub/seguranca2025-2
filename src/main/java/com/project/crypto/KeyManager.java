package com.project.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;


public class KeyManager {
    private static final Logger logger = LoggerFactory.getLogger(KeyManager.class);

    public record SessionKeys(SecretKey aesKey, SecretKey hmacKey) {}

    private final Map<String, SessionKeys> peerSessionKeys;
    private final KeyPair rsaKeyPair;
    private final Map<String, PublicKey> peerPublicKeys;

    public KeyManager() throws NoSuchAlgorithmException {
        logger.debug("Inicializando KeyManager...");
        this.rsaKeyPair = RSA.generateKeyPair();
        this.peerPublicKeys = new ConcurrentHashMap<>();
        this.peerSessionKeys = new ConcurrentHashMap<>();
        logger.debug("KeyManager inicializado com par de chaves RSA");
    }

    public void generateSessionKeys(String peerId) throws NoSuchAlgorithmException {
        logger.debug("Gerando chaves de sessão para peer '{}' ...", peerId);

        SecretKey aesKey = AES.generateKey();
        SecretKey hmacKey = HMAC.generateKey();
        peerSessionKeys.put(peerId, new SessionKeys(aesKey, hmacKey));

        logger.debug("Chaves de sessão (AES + HMAC) geradas e armazenadas para peer '{}' ", peerId);
    }

    public void storeSessionKeys(String peerId, SecretKey aesKey, SecretKey hmacKey) {
        logger.debug("Armazenando chaves de sessão para peer '{}' ...", peerId);

        peerSessionKeys.put(peerId, new SessionKeys(aesKey, hmacKey));

        logger.debug("Chaves de sessão armazenadas para peer '{}': AES + HMAC", peerId);
    }

    public SecretKey getPeerAESKey(String peerId) {
        logger.debug("Procurando chave AES de sessão para peer '{}' ...", peerId);

        SessionKeys keys = peerSessionKeys.get(peerId);
        SecretKey aesKey = keys != null ? keys.aesKey() : null;
        
        logger.debug("Resultado da busca: {}", aesKey != null ? "ENCONTRADA ✓" : "NÃO ENCONTRADA ✗");

        return aesKey;
    }

    public SecretKey getPeerHMACKey(String peerId) {
        logger.debug("Procurando chave HMAC de sessão para peer '{}' ...", peerId);

        SessionKeys keys = peerSessionKeys.get(peerId);
        SecretKey hmacKey = keys != null ? keys.hmacKey() : null;
        
        logger.debug("Resultado da busca: {}", hmacKey != null ? "ENCONTRADA ✓" : "NÃO ENCONTRADA ✗");

        return hmacKey;
    }

    public boolean hasSessionKeys(String peerId) {
        boolean hasKeys = peerSessionKeys.containsKey(peerId);

        logger.debug("Verificando existência de chaves de sessão para peer '{}' : {}",
        peerId,
        hasKeys ? "EXISTE ✓" : "NÃO EXISTE ✗");

        return hasKeys;
    }

    public String encryptSessionKeysForPeer(String peerId) throws GeneralSecurityException {
        logger.debug("Serializando e cifrando chaves de sessão para peer '{}'...", peerId);
        
        SessionKeys keys = peerSessionKeys.get(peerId);
        
        if (keys == null) {
            logger.error("Chaves de sessão não encontradas para peer: {}", peerId);
            throw new IllegalArgumentException("Chaves de sessão não encontradas para peer: " + peerId);
        }
        
        String aesBase64 = Base64.getEncoder().encodeToString(keys.aesKey().getEncoded());
        String hmacBase64 = Base64.getEncoder().encodeToString(keys.hmacKey().getEncoded());
        String keysData = aesBase64 + ":" + hmacBase64;
        
        byte[] encrypted = encryptForPeer(peerId, keysData.getBytes());
        String result = Base64.getEncoder().encodeToString(encrypted);
        
        logger.debug("Chaves de sessão cifradas para peer '{}': {} chars", peerId, result.length());
        return result;
    }

    public void decryptAndStoreSessionKeys(String peerId, String encryptedKeys) throws GeneralSecurityException {
        logger.debug("Decifrando e armazenando chaves de sessão do peer '{}'...", peerId);
        
        byte[] encrypted = Base64.getDecoder().decode(encryptedKeys);
        byte[] decrypted = decrypt(encrypted);
        String keysData = new String(decrypted);
        
        String[] parts = keysData.split(":");
        byte[] aesKeyBytes = Base64.getDecoder().decode(parts[0]);
        byte[] hmacKeyBytes = Base64.getDecoder().decode(parts[1]);
        
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        SecretKey hmacKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
        
        storeSessionKeys(peerId, aesKey, hmacKey);
        
        logger.debug("Chaves de sessão decifradas e armazenadas para peer '{}'", peerId);
    }

    public void storePeerKey(String peerId, PublicKey publicKey) {
        logger.debug("Armazenando chave pública de peer '{}' ...", peerId);

        peerPublicKeys.put(peerId, publicKey);
        logger.debug("Chave pública armazenada: peer '{}' ({} peers no total)",
                peerId,
                peerPublicKeys.size()
        );
    }

    public void storePeerKey(String peerId, String base64PublicKey) throws GeneralSecurityException {
        logger.debug("Convertendo e armazenando chave pública em Base64 de peer '{}' ...", peerId);

        PublicKey publicKey = RSA.base64ToPublicKey(base64PublicKey);
        storePeerKey(peerId, publicKey);
    }

    public PublicKey getPeerPublicKey(String peerId) {
        logger.debug("Recuperando chave pública de peer '{}' ...", peerId);

        PublicKey publicKey = peerPublicKeys.get(peerId);
        
        logger.debug("Resultado da busca: {}", publicKey != null ? "ENCONTRADA ✓" : "NÃO ENCONTRADA ✗");

        return publicKey;
    }

    public boolean hasPeerKey(String peerId) {
        boolean hasKey = peerPublicKeys.containsKey(peerId);

        logger.debug("Verificando existência de chave pública para peer '{}' : {}",
        peerId,
        hasKey ? "EXISTE ✓" : "NÃO EXISTE ✗");

        return hasKey;
    }

    public byte[] encryptForPeer(String peerId, byte[] data) throws GeneralSecurityException {
        logger.debug("Cifrando {} bytes para peer '{}'...", data.length, peerId);
        PublicKey peerKey = peerPublicKeys.get(peerId);
        if (peerKey == null) {
            logger.error("Chave pública não encontrada para peer: {}", peerId);
            throw new IllegalArgumentException("Chave pública não encontrada para peer: " + peerId);
        }
        byte[] encrypted = RSA.encrypt(data, peerKey);
        logger.debug("Dados cifrados para peer '{}': {} bytes -> {} bytes", 
            peerId, 
            data.length, 
            encrypted.length);
        return encrypted;
    }

    public byte[] decrypt(byte[] encryptedData) throws GeneralSecurityException {
        logger.debug("Decifrando {} bytes...", encryptedData.length);
        byte[] decrypted = RSA.decrypt(encryptedData, rsaKeyPair.getPrivate());
        logger.debug("Dados decifrados: {} bytes -> {} bytes", 
            encryptedData.length, 
            decrypted.length);
        return decrypted;
    }

    public byte[] sign(byte[] data) throws GeneralSecurityException {
        logger.debug("Assinando {} bytes...", data.length);
        byte[] signature = RSA.sign(data, rsaKeyPair.getPrivate());
        logger.debug("Dados assinados: {} bytes -> assinatura de {} bytes", 
            data.length, 
            signature.length);
        return signature;
    }

    public String signBase64(byte[] data) throws GeneralSecurityException {
        byte[] signature = sign(data);
        return Base64.getEncoder().encodeToString(signature);
    }

    public boolean verifySignature(String peerId, byte[] data, byte[] signature) throws GeneralSecurityException {
        logger.debug("Verificando assinatura do peer '{}' ({} bytes de dados)...", peerId, data.length);
        PublicKey peerKey = peerPublicKeys.get(peerId);
        if (peerKey == null) {
            logger.error("Chave pública não encontrada para peer: {}", peerId);
            throw new IllegalArgumentException("Chave pública não encontrada para peer: " + peerId);
        }
        boolean isValid = RSA.verify(data, signature, peerKey);
        logger.debug("Resultado da verificação para peer '{}': {}", 
            peerId, 
            isValid ? "VÁLIDA ✓" : "INVÁLIDA ✗");
        return isValid;
    }

    public PublicKey getPublicKey() {
        return rsaKeyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return rsaKeyPair.getPrivate();
    }

    public String getPublicKeyBase64() {
        return RSA.publicKeyToBase64(rsaKeyPair.getPublic());
    }

    public void clearPeerKeys(String peerId) {
        logger.debug("Removendo todas as chaves do peer '{}'...", peerId);
        
        peerPublicKeys.remove(peerId);
        peerSessionKeys.remove(peerId);
        
        logger.info("Chaves removidas para peer '{}' (públicas + sessão)", peerId);
    }
}
