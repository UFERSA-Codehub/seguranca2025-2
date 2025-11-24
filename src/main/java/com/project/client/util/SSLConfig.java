package com.project.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

public class SSLConfig {

    private static final Logger logger = LoggerFactory.getLogger(SSLConfig.class);

    private SSLContext sslContext;
    private HostnameVerifier hostnameVerifier;

    public SSLConfig(boolean verifyHostname, String truststorePath, String truststorePassword) throws Exception {
        initializeSSLContext(truststorePath, truststorePassword);
        initializeHostnameVerifier(verifyHostname);
    }

    private void initializeSSLContext(String truststorePath, String truststorePassword) throws Exception {
        TrustManager[] trustManagers;

        if (truststorePath != null && !truststorePath.isEmpty()) {
            // Usar truststore personalizado
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(truststorePath)) {
                trustStore.load(fis, truststorePassword.toCharArray());
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();

            logger.info("Usando truststore personalizado: {}", truststorePath);
        } else {
            // Modo desenvolvimento: aceitar todos os certificados
            trustManagers = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Aceitar qualquer certificado do cliente (desenvolvimento)
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Aceitar qualquer certificado do servidor (desenvolvimento)
                        logger.warn("Modo desenvolvimento: aceitando certificado auto-assinado");
                        logger.debug("Certificado - Subject: {}, Issuer: {}",
                            certs[0].getSubjectX500Principal().getName(),
                            certs[0].getIssuerX500Principal().getName());
                    }
                }
            };

            logger.info("Modo desenvolvimento: aceitando todos os certificados");
        }

        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, new SecureRandom());
    }

    private void initializeHostnameVerifier(boolean verifyHostname) {
        if (verifyHostname) {
            hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
            logger.info("Verificação de hostname habilitada");
        } else {
            hostnameVerifier = (hostname, session) -> {
                logger.warn("Verificação de hostname desabilitada, hostname esperado: {}", hostname);
                return true; // Aceitar qualquer hostname (desenvolvimento)
            };
        }
    }

    public void configureHttpsURLConnection(HttpsURLConnection conn) {
        conn.setSSLSocketFactory(sslContext.getSocketFactory());
        conn.setHostnameVerifier(hostnameVerifier);
    }

    public static SSLConfig createDevelopmentConfig() throws Exception {
        return new SSLConfig(false, null, null);
    }

    public static SSLConfig createProductionConfig(String truststorePath, String truststorePassword) throws Exception {
        return new SSLConfig(true, truststorePath, truststorePassword);
    }
}