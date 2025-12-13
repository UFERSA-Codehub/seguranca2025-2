package com.project.server.firewall;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.KeyManager;
import com.project.message.tcp.EnvelopeTCP;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;
import com.project.server.firewall.ContentInspector.InspectionResult;
import com.project.tracing.TraceEvent;
import com.project.tracing.TracerFactory;

public class ProxyHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("Firewall.ProxyHandler");

    private final Socket clientSocket;
    private final String targetHost;
    private final int targetPort;
    private final String serviceName;
    private final String clientIp;
    private final int clientPort;
    private final IdsClient idsClient;

    private volatile boolean running;

    public ProxyHandler(Socket clientSocket, String targetHost, int targetPort, 
                       String serviceName, IdsClient idsClient) {
        this.clientSocket = clientSocket;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.serviceName = serviceName;
        this.idsClient = idsClient;
        this.running = true;

        InetSocketAddress addr = (InetSocketAddress) clientSocket.getRemoteSocketAddress();
        this.clientIp = addr.getAddress().getHostAddress();
        this.clientPort = addr.getPort();
    }

    @Override
    public void run() {
        logger.info("Iniciando proxy: {} -> {}", clientIp, serviceName);

        Socket serverSocket = null;
        ProxySession session = null;

        try {
            // Conectar ao servidor interno
            serverSocket = new Socket();
            serverSocket.connect(new InetSocketAddress(targetHost, targetPort), 5000);
            serverSocket.setSoTimeout(30000);
            clientSocket.setSoTimeout(30000);

            // Estabelecer sessao criptografada em ambos os lados
            session = new ProxySession(clientSocket, serverSocket, clientIp, serviceName);
            
            if (!session.establish()) {
                logger.error("Falha ao estabelecer sessao proxy para {}", clientIp);
                return;
            }

            // Relay de mensagens
            relayMessages(session);

        } catch (IOException e) {
            logger.error("Erro no proxy para {}: {}", clientIp, e.getMessage());
        } finally {
            running = false;
            if (session != null) {
                session.close();
            }
            closeSocket(serverSocket);
            closeSocket(clientSocket);
            logger.debug("Proxy encerrado: {} -> {}", clientIp, serviceName);
        }
    }

    private void relayMessages(ProxySession session) {
        SecureTCPChannel clientChannel = session.getClientChannel();
        SecureTCPChannel serverChannel = session.getServerChannel();
        KeyManager sessionKeyManager = session.getKeyManager();
        String clientId = session.getClientId();
        String serverId = session.getServerId();

        clientChannel.setTracePeerId("PACKET_FILTER");
        serverChannel.setTracePeerId(serviceName);

        TracerFactory.getTracer().trace(TraceEvent.create(
            "REVERSE_PROXY",
            "TCP",
            "RECEIVE",
            clientIp + ":" + clientPort,
            null,
            "SESSION",
            null,
            null,
            "PACKET_FILTER"
        ));

        while (running && !clientSocket.isClosed() && !serverChannel.getSocket().isClosed()) {
            try {
                // Receber mensagem do cliente
                MessageTCP clientMessage = clientChannel.receive();
                if (clientMessage == null) {
                    logger.debug("Cliente {} desconectou", clientId);
                    break;
                }

                // Verificar integridade
                if (!clientChannel.verify(clientMessage)) {
                    logger.warn("Mensagem invalida de {}", clientId);
                    sendAlertToIds("INVALID_MESSAGE", "Mensagem com assinatura/HMAC invalido");
                    continue;
                }

                // Decifrar e inspecionar conteudo
                String decryptedPayload = null;
                MessageTypeTCP messageType = clientMessage.getType();

                if (clientChannel.isEncryptedEnvelope(clientMessage)) {
                    EnvelopeTCP envelope = clientChannel.decryptEnvelope(clientId, clientMessage);
                    if (envelope != null) {
                        decryptedPayload = envelope.getPayload();
                        messageType = envelope.getType();
                    }
                } else if (clientMessage.getEncryptedPayload() != null) {
                    decryptedPayload = clientChannel.decrypt(clientId, clientMessage);
                }

                // Inspecionar conteudo (apenas para DATA)
                if (messageType == MessageTypeTCP.DATA && decryptedPayload != null) {
                    InspectionResult result = ContentInspector.inspect(decryptedPayload, clientIp);
                    if (!result.valid()) {
                        logger.warn("Conteudo anomalo de {}: {} - BLOQUEANDO", clientIp, result.reason());
                        sendAlertToIds(result.alertType(), result.reason(), result.sensorId());
                        continue; // Drop silencioso - não informar atacante sobre detecção
                    }
                }

                // Re-encriptar e enviar ao servidor interno
                MessageTCP serverMessage = reencryptForServer(serverChannel, serverId, clientMessage, clientId, sessionKeyManager);
                if (serverMessage != null) {
                    // Tracing feito apenas no RECEIVE (possui payload cifrado e decifrado)
                    serverChannel.send(serverMessage);
                }

                // Aguardar resposta do servidor
                MessageTCP serverResponse = serverChannel.receive();
                if (serverResponse == null) {
                    logger.debug("Servidor {} desconectou", serverId);
                    break;
                }

                TracerFactory.getTracer().trace(TraceEvent.create(
                    "REVERSE_PROXY",
                    "TCP",
                    "RECEIVE",
                    targetHost + ":" + targetPort,
                    null,
                    serverResponse.getType() != null ? serverResponse.getType().name() : "ENVELOPE",
                    serverResponse.getEncryptedPayload(),
                    null,
                    serviceName
                ));

                // Verificar resposta do servidor
                if (serverChannel.verify(serverResponse)) {
                    // Re-encriptar e enviar ao cliente
                    MessageTCP clientResponse = reencryptForClient(clientChannel, clientId, serverResponse, serverId, sessionKeyManager);
                    if (clientResponse != null) {
                        // Tracing feito apenas no RECEIVE (possui payload cifrado e decifrado)
                        clientChannel.send(clientResponse);
                    }
                }

            } catch (Exception e) {
                if (running) {
                    logger.error("Erro no relay: {}", e.getMessage());
                }
                break;
            }
        }
    }

    private MessageTCP reencryptForServer(SecureTCPChannel serverChannel, String serverId, 
                                          MessageTCP originalMessage, String originalSenderId,
                                          KeyManager keyManager) {
        // Decifrar envelope do cliente e re-encriptar para o servidor
        if (originalMessage.getType() == null && originalMessage.getEncryptedPayload() != null) {
            // Envelope cifrado
            EnvelopeTCP envelope = decryptEnvelopeWithKeys(originalMessage, originalSenderId, keyManager);
            if (envelope != null) {
                return serverChannel.buildEncryptedEnvelope(serverId, envelope.getType(), 
                                                            envelope.getPayload(), envelope.getJwtToken());
            }
        }

        // Mensagem simples cifrada
        MessageTypeTCP type = originalMessage.getType();
        if (type != null && originalMessage.getEncryptedPayload() != null) {
            String payload = decryptWithKeys(originalMessage, originalSenderId, keyManager);
            if (payload != null) {
                return serverChannel.buildEncrypted(serverId, type, payload);
            }
        }

        // Mensagem sem payload cifrado
        if (type != null) {
            return serverChannel.buildEncrypted(serverId, type, "");
        }

        return null;
    }

    private MessageTCP reencryptForClient(SecureTCPChannel clientChannel, String clientId,
                                          MessageTCP serverResponse, String serverId,
                                          KeyManager keyManager) {
        // Decifrar envelope do servidor e re-encriptar para o cliente
        if (serverResponse.getType() == null && serverResponse.getEncryptedPayload() != null) {
            EnvelopeTCP envelope = decryptEnvelopeWithKeys(serverResponse, serverId, keyManager);
            if (envelope != null) {
                return clientChannel.buildEncryptedEnvelope(clientId, envelope.getType(),
                                                            envelope.getPayload(), envelope.getJwtToken());
            }
        }

        // Mensagem simples cifrada
        MessageTypeTCP type = serverResponse.getType();
        if (type != null && serverResponse.getEncryptedPayload() != null) {
            String payload = decryptWithKeys(serverResponse, serverId, keyManager);
            if (payload != null) {
                return clientChannel.buildEncrypted(clientId, type, payload);
            }
        }

        // Mensagem sem payload cifrado
        if (type != null) {
            return clientChannel.buildEncrypted(clientId, type, "");
        }

        return null;
    }

    private EnvelopeTCP decryptEnvelopeWithKeys(MessageTCP message, String peerId, KeyManager keyManager) {
        try {
            var aesKey = keyManager.getPeerAESKey(peerId);
            var aes = new com.project.crypto.AES(aesKey);
            String envelopeJson = aes.decrypt(message.getEncryptedPayload());
            return EnvelopeTCP.fromJson(envelopeJson);
        } catch (Exception e) {
            logger.debug("Falha ao decifrar envelope: {}", e.getMessage());
            return null;
        }
    }

    private String decryptWithKeys(MessageTCP message, String peerId, KeyManager keyManager) {
        try {
            var aesKey = keyManager.getPeerAESKey(peerId);
            var aes = new com.project.crypto.AES(aesKey);
            return aes.decrypt(message.getEncryptedPayload());
        } catch (Exception e) {
            logger.debug("Falha ao decifrar payload: {}", e.getMessage());
            return null;
        }
    }

    private void sendAlertToIds(String alertType, String reason) {
        sendAlertToIds(alertType, reason, null);
    }

    private void sendAlertToIds(String alertType, String reason, String sensorId) {
        if (idsClient != null) {
            idsClient.sendAlert(clientIp, clientPort, serviceName, alertType, reason, sensorId);
        }
    }

    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.debug("Erro ao fechar socket: {}", e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
    }
}
