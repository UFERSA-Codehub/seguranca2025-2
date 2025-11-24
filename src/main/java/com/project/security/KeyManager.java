package com.project.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class KeyManager {

    private static final String SHARED_SECRET = "MinhaSeedMassa";

    private static SecretKey aesKey = null;
    private static SecretKey hmacKey = null;
    private static RSA rsalocal = null;

    private static final Map<String, SessionKeys> sessionKeys = new ConcurrentHashMap<>();
    private static final Map<String, PublicKey> chavesPublicasConfiaveis = new ConcurrentHashMap<>();

    private static final long TEMPO_EXPIRACAO_SESSAO = 30 * 60 * 1000; // 30 minutos em milissegundos

    public static SecretKey getAESKey() {
        if (aesKey == null) {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                byte[] hashedSecret = sha256.digest(SHARED_SECRET.getBytes(StandardCharsets.UTF_8));
                byte[] aesKeyBytes = Arrays.copyOfRange(hashedSecret, 0, 16); // AES-128
                aesKey = new SecretKeySpec(aesKeyBytes, "AES");
                if (DebugConfig.DEBUG_MODE) {
                    System.out.println("");
                    System.out.println("🔑 AES Key (" + aesKeyBytes.length + " bytes): " + bytesToHex(aesKeyBytes));
                }
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 deu algo", e);
            }

        }
        return aesKey;
    }

    public static SecretKey getHMACKey() {
        if (hmacKey == null) {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                byte[] hashedSecret = sha256.digest(SHARED_SECRET.getBytes(StandardCharsets.UTF_8));
                byte[] hmacKeyBytes = Arrays.copyOfRange(hashedSecret, 0, 32); // Usar os 32 bytes do secret para HMAC
                hmacKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
                if (DebugConfig.DEBUG_MODE) {
                    System.out.println("🔑 HMAC Key (" + hmacKeyBytes.length + " bytes): " + bytesToHex(hmacKeyBytes));
                }

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 deu algo", e);
            }
        }

        return hmacKey;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static void corruptHMACKey() {
        hmacKey = new SecretKeySpec("ChaveMaligna".getBytes(), "HmacSHA256");
    }

    public static void initRSA() {
        if (rsalocal == null) {
            rsalocal = new RSA();
            rsalocal.gerarParDeChaves();
        
            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[KeyManager]: RSA inicializado");
            }
        }
    }

    public static RSA getRSALocal() {
        if (rsalocal == null) {
            initRSA();
        }
        return rsalocal;
    }

    public static PublicKey getChavePublicaRSA() {
        return getRSALocal().getChavePublica();
    }

    public static String getChavePublicaRSABase64() {
        return getRSALocal().exportarChavePublicaBase64();
    }

    public static void registrarChavePublicaConfiavel(String id, PublicKey chave) {
        chavesPublicasConfiaveis.put(id, chave);
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[KeyManager]: Chave pública confiável registrada para ID: " + id);
        }
    }

    public static void registrarChavePublicaConfiavel(String id, String chaveBase64) {
        PublicKey chave = RSA.importarChavePublicaBase64(chaveBase64);
        if (chave != null) {
            registrarChavePublicaConfiavel(id, chave);
        }
    }

    public static PublicKey obterChavePublicaConfiavel(String id) {
        return chavesPublicasConfiaveis.get(id);
    }

    public static boolean isChaveConfiavel(String id) {
        return chavesPublicasConfiaveis.containsKey(id);
    }

    public static SessionKeys criarChavesDaSessao(String idClient) {
        try {
            KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
            aesKeyGen.init(128);
            SecretKey sessionAesKey = aesKeyGen.generateKey();


            KeyGenerator hmacKeyGen = KeyGenerator.getInstance("HmacSHA256");
            SecretKey sessionHMAC = hmacKeyGen.generateKey();

            SessionKeys keys = new SessionKeys(
                    idClient,
                    sessionAesKey,
                    sessionHMAC,
                    System.currentTimeMillis(),
                    System.currentTimeMillis() + TEMPO_EXPIRACAO_SESSAO
            );

            sessionKeys.put(idClient, keys);

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[KeyManager]: Chaves de sessão criadas para ID: " + idClient);
                System.out.println("[KeyManager]: Expiram em: " + (TEMPO_EXPIRACAO_SESSAO / 1000 / 60) + " minutos");
            }

            return keys;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao gerar chaves de sessão", e);
        }
    }

    public static SessionKeys obterChavesDaSessao(String idClient) {
        SessionKeys keys = sessionKeys.get(idClient);
        if (keys != null && keys.isExpired()) {
            sessionKeys.remove(idClient);
            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[KeyManager]: Chaves de sessão expiraram para ID: " + idClient);
            }
            return null;
        }
        return keys;
    }

    public static void removerChavesDaSessao(String idClient) {
        sessionKeys.remove(idClient);
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[KeyManager]: Chaves de sessão removidas para ID: " + idClient);
        }
    }

    public static void limparSessoesExpiradas() {
        sessionKeys.entrySet().removeIf(entry -> {

            boolean expired = entry.getValue().isExpired();
            if (expired && DebugConfig.DEBUG_MODE) {
                System.out.println("[KeyManager]: Limpando sessão expirada: " + entry.getKey());
            }
            return expired;
        });
    }

    public static void registrarSessaoExterna(String idClient, SessionKeys keys) {
        if (keys == null) {
            throw new IllegalArgumentException("SessionKeys não pode ser nula");
        }
        sessionKeys.put(idClient, keys);
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[KeyManager]: Sessão externa registrada para ID: " + idClient);
            System.out.println("[KeyManager]: Expira em: " + keys.getTempoRestante() + " segundos");
        }
    }

    public static String diagnostico() {
        return String.format("[KeyManager]: Sessões ativas: %d, Chaves públicas confiáveis: %d",
                sessionKeys.size(),
                chavesPublicasConfiaveis.size());
    }


}
