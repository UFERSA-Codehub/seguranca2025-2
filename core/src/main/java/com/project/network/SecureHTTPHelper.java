package com.project.network;

import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.auth.JWT;
import com.project.crypto.AES;
import com.project.crypto.KeyManager;
import com.project.message.http.MessageHTTP;
import com.project.message.http.MessageTypeHTTP;

public class SecureHTTPHelper {
    private static final Logger logger = LoggerFactory.getLogger("Network.HTTPHelper");
    private final String serverId;
    private final KeyManager keyManager;
    private final JWT jwt;

    public SecureHTTPHelper(String serverId, KeyManager keyManager, JWT jwt) {
        this.serverId = serverId;
        this.keyManager = keyManager;
        this.jwt = jwt;
    }

    // ==================== HANDSHAKE ====================

    public MessageHTTP handleHello(MessageHTTP hello) {
        String clientId = hello.getClientId();
        try {
            keyManager.storePeerKey(clientId, hello.getSenderPublicKey());
            keyManager.generateSessionKeys(clientId);
            String encryptedKeys = keyManager.encryptSessionKeysForPeer(clientId);

            return MessageHTTP.builder()
                    .type(MessageTypeHTTP.CHALLENGE)
                    .clientId(serverId)
                    .senderPublicKey(keyManager.getPublicKeyBase64())
                    .encryptedSessionKeys(encryptedKeys)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao processar HELLO de '{}': {}", clientId, e.getMessage());
            return null;
        }
    }

    // ==================== BUILD ENCRYPTED ====================

    public MessageHTTP buildEncrypted(String clientId, MessageTypeHTTP type, String payload) {
        try {
            SecretKey aesKey = keyManager.getPeerAESKey(clientId);
            AES aes = new AES(aesKey);

            String encryptedPayload = aes.encrypt(payload);
            // Assinar ciphertext
            String signature = keyManager.signBase64(encryptedPayload.getBytes());

            return MessageHTTP.builder()
                    .type(type)
                    .clientId(serverId)
                    .encryptedPayload(encryptedPayload)
                    .signature(signature)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao construir mensagem cifrada para '{}': {}", clientId, e.getMessage());
            return null;
        }
    }

    // ==================== VERIFICAÇÃO (JWT -> RSA) ====================

    public boolean verify(MessageHTTP request) {
        String clientId = request.getClientId();

        // Passo 1 - Verificar JWT (mais barato)
        if (request.getJwtToken() != null) {
            if (!jwt.isValid(request.getJwtToken())) {
                logger.warn("JWT inválido de '{}'", clientId);
                return false;
            }
        }

        // Passo 2 - Verificar assinatura RSA do ciphertext (autenticidade + integridade)
        if (!verifySignature(clientId, request)) {
            return false;
        }

        return true;
    }

    public boolean verifyWithoutJwt(MessageHTTP request) {
        String clientId = request.getClientId();
        return verifySignature(clientId, request);
    }

    public boolean verifySignature(String clientId, MessageHTTP request) {
        if (!keyManager.hasSessionKeys(clientId)) {
            logger.warn("Sem chaves de sessão para '{}' - handshake necessário", clientId);
            return false;
        }

        try {
            byte[] ciphertextBytes = request.getEncryptedPayload().getBytes();
            byte[] signatureBytes = Base64.getDecoder().decode(request.getSignature());

            if (!keyManager.verifySignature(clientId, ciphertextBytes, signatureBytes)) {
                logger.warn("Assinatura inválida de '{}'", clientId);
                return false;
            }
            return true;
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao verificar assinatura de '{}': {}", clientId, e.getMessage());
            return false;
        }
    }

    // ==================== DECRYPT ====================

    public String decrypt(String clientId, MessageHTTP request) {
        try {
            AES aes = new AES(keyManager.getPeerAESKey(clientId));
            return aes.decrypt(request.getEncryptedPayload());
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao decifrar mensagem de '{}': {}", clientId, e.getMessage());
            return null;
        }
    }

    // ==================== UTILITY ====================

    public boolean hasSession(String clientId) {
        return keyManager.hasSessionKeys(clientId);
    }

    public String getUsernameFromToken(String token) {
        return jwt.getSensorId(token);
    }

    public boolean isValidToken(String token) {
        return jwt.isValid(token);
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }
}
