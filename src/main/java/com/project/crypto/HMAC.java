package com.project.crypto;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class HMAC {
    private SecretKey key;
    private KeyGenerator keyGen;
    public static final String ALG = "HmacSHA256";
    // public static final String ALG = "HmacSHA224";
    // public static final String ALG = "HmacSHA384";
    // public static final String ALG = "HmacSHA512";

    public HMAC() {
        //generateKey();
    }

    public void generateKey() {
        try {
            keyGen = KeyGenerator.getInstance(ALG);
            key = keyGen.generateKey();
            System.out.println("Chave HMAC gerada: " + key.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String generateHMAC(String mensagem) {
        String result = null;

        try {
            Mac shaHMAC = Mac.getInstance(ALG);
            shaHMAC.init(key);
            byte[] bytesHMAC = shaHMAC
                    .doFinal(mensagem.getBytes("UTF-8"));
            result = byte2Hex(bytesHMAC);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    // public static String hMac(String chave, String mensagem) throws Exception {
    //     Mac shaHMAC = Mac.getInstance(ALG);
    //     SecretKeySpec chaveMAC = new SecretKeySpec(
    //             chave.getBytes("UTF-8"),
    //             ALG);
    //     shaHMAC.init(chaveMAC);
    //     byte[] bytesHMAC = shaHMAC
    //             .doFinal(mensagem.getBytes("UTF-8"));
    //     return byte2Hex(bytesHMAC);
    // }

    public static String byte2Hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public boolean verifyHMAC(String mensagem, String hmac) {
        String novoHmac = generateHMAC(mensagem);
        return novoHmac != null && novoHmac.equals(hmac);
    }

    public void setKey(SecretKey key) {
        this.key = key;
    }

    public SecretKey getKey() {
        return this.key;
    }
}