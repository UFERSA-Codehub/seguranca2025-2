package com.project.network;

import java.io.IOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import java.security.GeneralSecurityException;

import java.util.Base64;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.AES;
import com.project.crypto.HMAC;
import com.project.crypto.KeyManager;

import com.project.message.udp.EnvelopeUDP;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;

import com.project.tracing.TraceEvent;
import com.project.tracing.TracerFactory;

public class SecureUDPChannel {
    private static final int BUFFER_SIZE = 4096;
    private static final Logger logger = LoggerFactory.getLogger("Network.UDPChannel");
    private final String entityId;
    private final KeyManager keyManager;
    private final DatagramSocket socket;
    private String tracePeerId;
    
    // Store last received packet's address for tracing encrypted envelopes
    private String lastReceivedAddress;

    public SecureUDPChannel(String entityId, KeyManager keyManager, DatagramSocket socket) {
        this.entityId = entityId;
        this.keyManager = keyManager;
        this.socket = socket;
        this.tracePeerId = null;
        this.lastReceivedAddress = null;
    }

    public void setTracePeerId(String peerId) {
        this.tracePeerId = peerId;
    }

    // ==================== ENVIO ====================
    public void send(MessageUDP message, InetAddress address, int port) {
        try {
            byte[] data = message.toJson().getBytes();
            socket.send(new DatagramPacket(data, data.length, address, port));
            logger.debug("Mensagem {} enviada para {}:{}", message.getType(), address.getHostAddress(), port);
            // Tracing feito apenas no RECEIVE (possui payload cifrado e decifrado)
        } catch (IOException e) {
            logger.error("Erro ao enviar para {}:{} - {}", address.getHostAddress(), port, e.getMessage());
        }
    }
    public void send(MessageUDP message, String host, int port) {
        try {
            send(message, InetAddress.getByName(host), port);
        } catch (IOException e) {
            logger.error("Host desconhecido: {}", host);
        }
    }
    // ==================== RECEBIMENTO ====================
    public ReceivedPacket receive() {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String json = new String(packet.getData(), 0, packet.getLength());
            MessageUDP message = MessageUDP.fromJson(json);
            
            // Store the address for later use in decryptEnvelope tracing
            this.lastReceivedAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();
            
            // Traçar apenas mensagens nao cifradas (HELLO, CHALLENGE)
            // Mensagens cifradas serao traçadas apos a decifragem
            // Para HELLO: sempre usar senderId (é uma nova conexão, tracePeerId pode estar obsoleto)
            // Para outras mensagens: usar tracePeerId se disponível
            if (message.getEncryptedPayload() == null) {
                boolean isHello = message.getType() == MessageTypeUDP.HELLO;
                String peerId = isHello ? message.getSenderId() 
                    : (tracePeerId != null ? tracePeerId : message.getSenderId());
                String localAddr = socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort();
                TracerFactory.getTracer().trace(TraceEvent.create(
                    entityId,
                    "UDP",
                    "RECEIVE",
                    lastReceivedAddress,
                    localAddr,
                    message.getType() != null ? message.getType().name() : "UNKNOWN",
                    null,
                    null,
                    peerId
                ));
            }
            
            return new ReceivedPacket(message, packet.getAddress(), packet.getPort());
        } catch (SocketTimeoutException e) {
            logger.warn("Timeout aguardando mensagem");
            return null;
        } catch (IOException e) {
            logger.error("Erro ao receber: {}", e.getMessage());
            return null;
        }
    }
    // ==================== MENSAGENS CIFRADAS ====================
    public boolean verify(MessageUDP message) {
        String senderId = message.getSenderId();
        if (!keyManager.hasSessionKeys(senderId)) {
            logger.warn("Sem chaves de sessão para '{}' - handshake necessário", senderId);
            return false;
        }
        try {
            String encryptedPayload = message.getEncryptedPayload();
            
            // Passo 1 - Verificar HMAC (integridade simétrica)
            SecretKey hmacKey = keyManager.getPeerHMACKey(senderId);
            HMAC hmac = new HMAC(hmacKey);
            if (!hmac.verify(encryptedPayload, message.getHmac())) {
                logger.warn("HMAC inválido de '{}'", senderId);
                return false;
            }
            
            // Passo 2 - Verificar assinatura RSA (autenticidade)
            byte[] ciphertextBytes = encryptedPayload.getBytes();
            byte[] signatureBytes = Base64.getDecoder().decode(message.getSignature());
            if (!keyManager.verifySignature(senderId, ciphertextBytes, signatureBytes)) {
                logger.warn("Assinatura inválida de '{}'", senderId);
                return false;
            }
            
            return true;
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao verificar mensagem de '{}': {}", senderId, e.getMessage());
            return false;
        }
    }

    // ==================== ENVELOPE CIFRADO ====================
    
    public MessageUDP buildEncryptedEnvelope(String peerId, MessageTypeUDP type, String payload, String jwtToken) {
        try {
            // Passo 1 - Criar envelope com dados sensíveis
            EnvelopeUDP envelope = EnvelopeUDP.builder()
                    .type(type)
                    .payload(payload)
                    .jwtToken(jwtToken)
                    .nonce(java.util.UUID.randomUUID().toString())
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Passo 2 - Cifrar envelope com AES
            SecretKey aesKey = keyManager.getPeerAESKey(peerId);
            SecretKey hmacKey = keyManager.getPeerHMACKey(peerId);
            
            AES aes = new AES(aesKey);
            HMAC hmac = new HMAC(hmacKey);

            String envelopeJson = envelope.toJson();
            String encryptedEnvelope = aes.encrypt(envelopeJson);

            // Passo 3 - Gerar HMAC e assinar ciphertext
            String hmacValue = hmac.sign(encryptedEnvelope);
            String signature = keyManager.signBase64(encryptedEnvelope.getBytes());

            // Passo 4 - Construir mensagem externa (type=null indica envelope cifrado)
            return MessageUDP.builder()
                    .type(null)
                    .senderId(entityId)
                    .encryptedPayload(encryptedEnvelope)
                    .hmac(hmacValue)
                    .signature(signature)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao construir envelope cifrado para '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public MessageUDP buildEncryptedEnvelope(String peerId, MessageTypeUDP type, String payload) {
        return buildEncryptedEnvelope(peerId, type, payload, null);
    }

    public EnvelopeUDP decryptEnvelope(String peerId, MessageUDP message) {
        try {
            AES aes = new AES(keyManager.getPeerAESKey(peerId));
            String envelopeJson = aes.decrypt(message.getEncryptedPayload());
            EnvelopeUDP envelope = EnvelopeUDP.fromJson(envelopeJson);
            
            // Skip tracing for HEARTBEAT messages (too noisy, not useful for topology)
            if (envelope.getType() != MessageTypeUDP.HEARTBEAT) {
                // Usar tracePeerId se definido (para contexto específico), senão usar peerId
                String traceTargetPeerId = tracePeerId != null ? tracePeerId : peerId;
                // Usar lastReceivedAddress (IP:port real) em vez de peerId para remoteAddress
                String remoteAddr = lastReceivedAddress != null ? lastReceivedAddress : peerId;
                String localAddr = socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort();
                TracerFactory.getTracer().trace(TraceEvent.create(
                    entityId,
                    "UDP",
                    "RECEIVE",
                    remoteAddr,
                    localAddr,
                    envelope.getType() != null ? envelope.getType().name() : "ENVELOPE",
                    message.getEncryptedPayload(),
                    envelopeJson,
                    traceTargetPeerId
                ));
            }
            
            return envelope;
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao decifrar envelope de '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public boolean isEncryptedEnvelope(MessageUDP message) {
        return message.getType() == null && message.getEncryptedPayload() != null;
    }

    // ==================== HANDSHAKE ====================
    public MessageUDP buildHello() {
        return MessageUDP.builder()
                .type(MessageTypeUDP.HELLO)
                .senderId(entityId)
                .senderPublicKey(keyManager.getPublicKeyBase64())
                .build();
    }
    public MessageUDP handleHello(MessageUDP hello) {
        String peerId = hello.getSenderId();
        try {
            keyManager.storePeerKey(peerId, hello.getSenderPublicKey());
            keyManager.generateSessionKeys(peerId);
            String encryptedKeys = keyManager.encryptSessionKeysForPeer(peerId);
            return MessageUDP.builder()
                    .type(MessageTypeUDP.CHALLENGE)
                    .senderId(entityId)
                    .senderPublicKey(keyManager.getPublicKeyBase64())
                    .encryptedSessionKeys(encryptedKeys)
                    .build();
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao processar HELLO de '{}': {}", peerId, e.getMessage());
            return null;
        }
    }

    public boolean handleChallenge(MessageUDP challenge) {
        String peerId = challenge.getSenderId();
        try {
            keyManager.storePeerKey(peerId, challenge.getSenderPublicKey());
            keyManager.decryptAndStoreSessionKeys(peerId, challenge.getEncryptedSessionKeys());
            return true;
        } catch (GeneralSecurityException e) {
            logger.error("Erro ao processar CHALLENGE de '{}': {}", peerId, e.getMessage());
            return false;
        }
    }
    // ==================== GETTERS ====================
    public DatagramSocket getSocket() { return socket; }

    public void clearPeerSession(String peerId) {
        keyManager.clearPeerKeys(peerId);
    }

    // ==================== RECORD ====================
    public record ReceivedPacket(MessageUDP message, InetAddress address, int port) {}
}