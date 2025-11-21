package com.project.security;

import javax.crypto.Cipher;
import java.security.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class RSA {
    private KeyPair parDeChaves;
    private static final int TAMANHO_DA_CHAVE = 2048;

    public RSA() {
     
    }

    public void gerarParDeChaves() {
        try {
            KeyPairGenerator geradorDeParDeChaves = KeyPairGenerator.getInstance("RSA");
            geradorDeParDeChaves.initialize(TAMANHO_DA_CHAVE);
            parDeChaves = geradorDeParDeChaves.generateKeyPair();
            //System.out.println("Par de chaves RSA gerado.");

            if (DebugConfig.DEBUG_MODE){
                System.out.println("[RSA]: Par de chaves RSA gerado (" + TAMANHO_DA_CHAVE + " bits)");
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public void setParDeChaves(KeyPair parDeChaves) {
        this.parDeChaves = parDeChaves;
    }

    public KeyPair getParDeChaves() {
        return parDeChaves;
    }

    public PublicKey getChavePublica() {
        return parDeChaves != null ? parDeChaves.getPublic() : null;
    }

    public PrivateKey getChavePrivada() {
        return parDeChaves != null ? parDeChaves.getPrivate() : null;
    }

    public byte[] cifrar(byte[] dados, PublicKey chavePublica) {
        try {
            Cipher cifrador = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cifrador.init(Cipher.ENCRYPT_MODE, chavePublica);
            return cifrador.doFinal(dados);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String cifrar(String textoAberto, PublicKey chavePublica) {
        byte[] dadosCifrados = cifrar(textoAberto.getBytes(StandardCharsets.UTF_8), chavePublica);
        if (dadosCifrados == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(dadosCifrados);
    }

    public byte[] decifrar(byte[] dadosCifrados) {
        if (parDeChaves == null) {
            throw new IllegalStateException("Par de chaves não gerado.");
        }

        try {
            Cipher decifrador = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            decifrador.init(Cipher.DECRYPT_MODE, parDeChaves.getPrivate());
            return decifrador.doFinal(dadosCifrados);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String decifrar(String textoCifrado) {
        byte[] dadosCifrados = Base64.getDecoder().decode(textoCifrado);
        byte[] dadosDecifrados = decifrar(dadosCifrados);
        if (dadosDecifrados == null) {
            return null;
        }
        return new String(dadosDecifrados, StandardCharsets.UTF_8);
    }

    public String exportarChavePublicaBase64() {
        if (parDeChaves == null) {
            throw new IllegalStateException("Par de chaves não gerado.");
        }
        return Base64.getEncoder().encodeToString(parDeChaves.getPublic().getEncoded());
    }

    public static PublicKey importarChavePublicaBase64(String chaveBase64) {
        try {
            byte[] chaveBytes = Base64.getDecoder().decode(chaveBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new java.security.spec.X509EncodedKeySpec(chaveBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}