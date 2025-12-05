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

    private final Map<String, SecretKey> peerAESKeys;
    private final KeyPair rsaKeyPair;
    private final Map<String, PublicKey> peerPublicKeys;

    public KeyManager() throws NoSuchAlgorithmException {
        logger.debug("Inicializando KeyManager...");
        this.rsaKeyPair = RSA.generateKeyPair();
        this.peerPublicKeys = new ConcurrentHashMap<>();
        this.peerAESKeys = new ConcurrentHashMap<>();
        logger.debug("KeyManager inicializado com par de chaves RSA");
    }

    public void generateSessionKeys(String peerId) throws NoSuchAlgorithmException {
        logger.debug("Gerando chave de sessão para peer '{}' ...", peerId);

        SecretKey aesKey = AES.generateKey();
        peerAESKeys.put(peerId, aesKey);

        logger.debug("Chave de sessão gerada e armazenada para peer '{}' ", peerId);
    }

    public void storeSessionKeys(String peerId, SecretKey aesKey) {
        logger.debug("Armazenando chave de sessão para peer '{}' ...", peerId);

        peerAESKeys.put(peerId, aesKey);

        logger.debug("Chave de sessão armazenada para peer '{}': AES", peerId);
    }

    public SecretKey getPeerAESKey(String peerId) {
        logger.debug("Procurando chave AES de sessão para peer '{}' ...", peerId);

        SecretKey aesKey = peerAESKeys.get(peerId);
        
        logger.debug("Resultado da busca: {}", aesKey != null ? "ENCONTRADA ✓" : "NÃO ENCONTRADA ✗");

        return aesKey;
    }

    public boolean hasSessionKeys(String peerId) {
        boolean hasKeys = peerAESKeys.containsKey(peerId);

        logger.debug("Verificando existência de chave de sessão para peer '{}' : {}",
        peerId,
        hasKeys ? "EXISTE ✓" : "NÃO EXISTE ✗");

        return hasKeys;
    }

    public String encryptSessionKeysForPeer(String peerId) throws GeneralSecurityException {
        logger.debug("Serializando e cifrando chave de sessão para peer '{}'...", peerId);
        
        SecretKey aesKey = peerAESKeys.get(peerId);
        
        if (aesKey == null) {
            logger.error("Chave de sessão não encontrada para peer: {}", peerId);
            throw new IllegalArgumentException("Chave de sessão não encontrada para peer: " + peerId);
        }
        
        String keysData = Base64.getEncoder().encodeToString(aesKey.getEncoded());
        
        byte[] encrypted = encryptForPeer(peerId, keysData.getBytes());
        String result = Base64.getEncoder().encodeToString(encrypted);
        
        logger.debug("Chave de sessão cifrada para peer '{}': {} chars", peerId, result.length());
        return result;
    }

    public void decryptAndStoreSessionKeys(String peerId, String encryptedKeys) throws GeneralSecurityException {
        logger.debug("Decifrando e armazenando chave de sessão do peer '{}'...", peerId);
        
        byte[] encrypted = Base64.getDecoder().decode(encryptedKeys);
        byte[] decrypted = decrypt(encrypted);
        String keysData = new String(decrypted);
        
        byte[] aesKeyBytes = Base64.getDecoder().decode(keysData);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        
        storeSessionKeys(peerId, aesKey);
        
        logger.debug("Chave de sessão decifrada e armazenada para peer '{}'", peerId);
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
        peerAESKeys.remove(peerId);
        
        logger.info("Chaves removidas para peer '{}' (públicas + sessão)", peerId);
    }
}
