package com.project.server.firewall;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.KeyManager;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;

public class ProxySession {
    private static final Logger logger = LoggerFactory.getLogger("Firewall.ProxySession");

    private final Socket clientSocket;
    private final Socket serverSocket;
    private final String clientIp;
    private final String serviceName;

    // Cada sessao tem seu proprio KeyManager para evitar colisao de chaves
    private KeyManager sessionKeyManager;
    private SecureTCPChannel clientChannel;
    private SecureTCPChannel serverChannel;
    private String clientId;
    private String serverId;
    private String proxySessionId;
    private boolean established;

    public ProxySession(Socket clientSocket, Socket serverSocket, 
                       String clientIp, String serviceName) {
        this.clientSocket = clientSocket;
        this.serverSocket = serverSocket;
        this.clientIp = clientIp;
        this.serviceName = serviceName;
        this.established = false;
    }

    public boolean establish() {
        try {
            // Criar KeyManager proprio
            this.sessionKeyManager = new KeyManager();
            
            // Passo 1 - Criar canal com cliente (impersonando o serviço interno)
            // entityId = serviceName para que o cliente receba CHALLENGE com senderId correto (ex: AUTH)
            // traceEntityId = REVERSE_PROXY para que o tracing mostre corretamente quem esta processando
            clientChannel = new SecureTCPChannel(serviceName.toUpperCase(), sessionKeyManager, clientSocket);
            clientChannel.setTraceEntityId("REVERSE_PROXY");
            // Definir tracePeerId para clientChannel - comunica com PacketFilter/cliente externo
            clientChannel.setTracePeerId("PACKET_FILTER");

            // Passo 2 - Receber HELLO do cliente
            MessageTCP clientHello = clientChannel.receive();
            if (clientHello == null || clientHello.getType() != MessageTypeTCP.HELLO) {
                logger.warn("Esperava HELLO do cliente, recebeu: {}", 
                          clientHello != null ? clientHello.getType() : "null");
                return false;
            }
            clientId = clientHello.getSenderId();
            proxySessionId = "RP-" + clientId;
            logger.debug("Recebido HELLO de {} (sessao: {})", clientId, proxySessionId);

            // Passo 3 - Responder CHALLENGE ao cliente (impersonando o servico)
            MessageTCP challengeToClient = clientChannel.handleHello(clientHello);
            if (challengeToClient == null) {
                logger.error("Falha ao gerar CHALLENGE para cliente");
                return false;
            }
            clientChannel.send(challengeToClient);
            logger.debug("CHALLENGE enviado para {} (como {})", clientId, serviceName.toUpperCase());

            // Passo 4 - Criar canal com servidor interno
            // Usar proxySessionId (RP-<clientId>) para identificar unicamente esta sessao
            // Isso evita colisao de chaves quando multiplos clientes conectam simultaneamente
            serverChannel = new SecureTCPChannel(proxySessionId, sessionKeyManager, serverSocket);
            serverChannel.setTraceEntityId("REVERSE_PROXY");
            // Definir tracePeerId para o servidor com quem estamos comunicando
            serverChannel.setTracePeerId(serviceName.toUpperCase());

            // Passo 5 - Enviar HELLO ao servidor (proxy atua como cliente)
            MessageTCP helloToServer = serverChannel.buildHello();
            serverChannel.send(helloToServer);
            logger.debug("HELLO enviado para {}", serviceName);

            // Passo 6 - Receber CHALLENGE do servidor
            MessageTCP serverChallenge = serverChannel.receive();
            if (serverChallenge == null || serverChallenge.getType() != MessageTypeTCP.CHALLENGE) {
                logger.warn("Esperava CHALLENGE do servidor, recebeu: {}", 
                          serverChallenge != null ? serverChallenge.getType() : "null");
                return false;
            }
            serverId = serverChallenge.getSenderId();

            // Passo 7 - Processar CHALLENGE do servidor
            if (!serverChannel.handleChallenge(serverChallenge)) {
                logger.error("Falha ao processar CHALLENGE do servidor");
                return false;
            }
            logger.debug("Sessao estabelecida com {}", serverId);

            established = true;
            logger.info("ProxySession estabelecida: {} <-> Proxy ({}) <-> {}", clientId, serviceName, serverId);
            return true;

        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao criar KeyManager para sessao: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            logger.error("Erro ao estabelecer ProxySession: {}", e.getMessage());
            return false;
        }
    }

    public SecureTCPChannel getClientChannel() {
        return clientChannel;
    }

    public SecureTCPChannel getServerChannel() {
        return serverChannel;
    }

    public String getClientId() {
        return clientId;
    }

    public String getServerId() {
        return serverId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getServiceName() {
        return serviceName;
    }

    public boolean isEstablished() {
        return established;
    }

    public KeyManager getKeyManager() {
        return sessionKeyManager;
    }

    public void close() {
        established = false;
        
        if (clientChannel != null) {
            clientChannel.close();
        }
        
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        // KeyManager da sessao sera garbage collected junto com a sessao
        sessionKeyManager = null;
    }
}
