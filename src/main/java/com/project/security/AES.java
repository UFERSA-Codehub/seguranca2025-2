package com.project.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.KeyGenerator;
import javax.crypto.Cipher;
import java.security.*;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;


 public class AES {
        private KeyGenerator geradorDeChaves;
        private SecretKey chave;

        public AES() {
            //gerarChave();
        }

        public void gerarChave() {
            try {
                geradorDeChaves = KeyGenerator
                        .getInstance("AES");
                chave = geradorDeChaves
                        .generateKey();
                System.out.println("Chave gerada: "
                        + chave.toString());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        public String cifrar(String textoAberto) {
            String mensagemCifrada = null;
            byte[] bytesMensagemCifrada;
            Cipher cifrador;
            // Encripta mensagem
            try {
                cifrador = Cipher
                        .getInstance("AES/CBC/PKCS5Padding");

                byte[] iv = new byte[16];
                SecureRandom random = new SecureRandom();
                random.nextBytes(iv);
                IvParameterSpec ivParams = new IvParameterSpec(iv);

                cifrador.init(Cipher.ENCRYPT_MODE, chave, ivParams);
                bytesMensagemCifrada = cifrador.doFinal(textoAberto.getBytes());

                byte[] mensagemComIv = new byte[iv.length + bytesMensagemCifrada.length];
                System.arraycopy(iv, 0, mensagemComIv, 0, iv.length);
                System.arraycopy(bytesMensagemCifrada, 0, mensagemComIv, iv.length, bytesMensagemCifrada.length);

                mensagemCifrada = Base64
                        .getEncoder()
                        .encodeToString(mensagemComIv);
                        
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
            return mensagemCifrada;
        }

        public String decifrar(String textoCifrado) {
            String mensagem = null;
            // Decriptação
            byte[] bytesMensagemCifradaComIv = Base64
                    .getDecoder()
                    .decode(textoCifrado);
            Cipher decriptador;
            try {
                byte[] iv = new byte[16];
                System.arraycopy(bytesMensagemCifradaComIv, 0, iv, 0, iv.length);
                IvParameterSpec ivParams = new IvParameterSpec(iv);

                byte[] bytesMensagemCifrada = new byte[bytesMensagemCifradaComIv.length - iv.length];
                System.arraycopy(bytesMensagemCifradaComIv, iv.length, bytesMensagemCifrada, 0, bytesMensagemCifrada.length);

                decriptador = Cipher.getInstance("AES/CBC/PKCS5Padding");
                decriptador.init(Cipher.DECRYPT_MODE, chave, ivParams);
                byte[] bytesMensagemDecifrada = decriptador.doFinal(bytesMensagemCifrada);
                mensagem = new String(bytesMensagemDecifrada);
                /*
                 * System.out.println("<< Mensagem decifrada = "
                 * + mensagemDecifrada);
                 */
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
            return mensagem;
        }

        public void setChave(SecretKey chave) {
            this.chave = chave;
        }
        public SecretKey getChave() {
            return this.chave;
        }
    }
