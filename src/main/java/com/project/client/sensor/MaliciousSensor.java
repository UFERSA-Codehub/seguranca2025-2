package com.project.client.sensor;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.message.udp.MessageTypeUDP;
import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;

public class MaliciousSensor {
    private static final Logger logger = LoggerFactory.getLogger("MaliciousSensor");
    private static final int TIMEOUT_MS = 5000;

    private final String sensorId;
    private final String discoveryHost;
    private final int discoveryPort;
    private SecureUDPChannel channel;
    private String edgeHost;
    private int edgePort;

    public MaliciousSensor(String sensorId, String discoveryHost, int discoveryPort) {
        this.sensorId = sensorId;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
    }

    public void runAllAttacks() {
        logger.info("=== INICIANDO TESTES DE INTRUSÃO ===");
        logger.info("Sensor malicioso: {}", sensorId);

        try {
            KeyManager keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            this.channel = new SecureUDPChannel(sensorId, keyManager, socket);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        } catch (SocketException e) {
            logger.error("Erro ao abrir socket: {}", e.getMessage());
            return;
        }

        try {
            // Passo 1 - Handshake com Discovery e Edge
            if (!handshakeWithDiscovery()) {
                logger.error("Falha no handshake com Discovery");
                return;
            }
            if (!discoverEdge()) {
                logger.error("Falha ao descobrir Edge");
                return;
            }
            if (!handshakeWithEdge()) {
                logger.error("Falha no handshake com Edge");
                return;
            }

            // Passo 2 - Executar cenários de ataque
            logger.info("");
            logger.info("=== TESTE 1: Credenciais Inválidas ===");
            attackInvalidCredentials();

            logger.info("");
            logger.info("=== TESTE 2: JWT Token Forjado ===");
            attackForgedJWT();

            logger.info("");
            logger.info("=== TESTE 3: Mensagem Adulterada (assinatura inválida) ===");
            attackTamperedMessage();

            logger.info("");
            logger.info("=== TESTE 4: Dados sem Autenticação ===");
            attackWithoutAuth();

            logger.info("");
            logger.info("=== TESTES DE INTRUSÃO CONCLUÍDOS ===");
        } finally {
            channel.getSocket().close();
        }
    }

    private boolean handshakeWithDiscovery() {
        logger.info("Handshake com Discovery...");
        channel.send(channel.buildHello(), discoveryHost, discoveryPort);
        
        ReceivedPacket packet = channel.receive();
        if (packet == null) return false;
        
        MessageUDP challenge = packet.message();
        if (challenge == null || challenge.getType() != MessageTypeUDP.CHALLENGE) {
            return false;
        }
        return channel.handleChallenge(challenge);
    }

    private boolean discoverEdge() {
        logger.info("Buscando Edge...");
        MessageUDP lookEdge = channel.buildEncryptedEnvelope("DISCOVERY", MessageTypeUDP.LOOK_EDGE, "");
        if (lookEdge == null) return false;
        
        channel.send(lookEdge, discoveryHost, discoveryPort);
        
        ReceivedPacket packet = channel.receive();
        if (packet == null) return false;
        
        MessageUDP response = packet.message();
        if (response == null) return false;

        // Verificar e decifrar envelope
        if (!channel.verify(response)) {
            logger.error("Falha ao verificar resposta do Discovery");
            return false;
        }

        var envelope = channel.decryptEnvelope("DISCOVERY", response);
        if (envelope == null) {
            logger.error("Falha ao decifrar envelope do Discovery");
            return false;
        }

        if (envelope.getType() == MessageTypeUDP.NOT_FOUND) {
            logger.error("Nenhum Edge disponível");
            return false;
        }
        
        String[] parts = envelope.getPayload().split(":");
        this.edgeHost = parts[0];
        this.edgePort = Integer.parseInt(parts[1]);
        logger.info("Edge encontrado: {}:{}", edgeHost, edgePort);
        return true;
    }

    private boolean handshakeWithEdge() {
        logger.info("Handshake com Edge...");
        channel.send(channel.buildHello(), edgeHost, edgePort);
        
        ReceivedPacket packet = channel.receive();
        if (packet == null) return false;
        
        MessageUDP challenge = packet.message();
        if (challenge == null || challenge.getType() != MessageTypeUDP.CHALLENGE) {
            return false;
        }
        return channel.handleChallenge(challenge);
    }

    // Ataque 1 - Credenciais inválidas (esperado: AUTH_FAIL)
    private void attackInvalidCredentials() {
        logger.info("Tentando autenticar com credenciais inválidas...");
        
        JsonObject authPayload = new JsonObject();
        authPayload.addProperty("sensorId", "SENSOR_FAKE");
        authPayload.addProperty("password", "senhaErrada123");
        
        MessageUDP authMsg = channel.buildEncryptedEnvelope("EDGE", MessageTypeUDP.AUTH, authPayload.toString());
        if (authMsg == null) {
            logger.error("Falha ao construir mensagem AUTH");
            return;
        }
        channel.send(authMsg, edgeHost, edgePort);
        
        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.info("[RESULTADO] Timeout - servidor não respondeu");
            return;
        }
        
        MessageUDP response = packet.message();
        
        // Decifrar envelope para verificar resposta
        var envelope = channel.decryptEnvelope("EDGE", response);
        if (envelope == null) {
            logger.info("[RESULTADO] Falha ao decifrar resposta");
            return;
        }

        if (envelope.getType() == MessageTypeUDP.AUTH_FAIL) {
            logger.info("[SUCESSO] Ataque BLOQUEADO - AUTH_FAIL recebido");
        } else if (envelope.getType() == MessageTypeUDP.AUTH_OK) {
            logger.error("[FALHA] Ataque NÃO bloqueado - AUTH_OK recebido!");
        } else {
            logger.info("[RESULTADO] Resposta: {}", envelope.getType());
        }
    }

    // Ataque 2 - JWT forjado (esperado: mensagem ignorada)
    private void attackForgedJWT() {
        logger.info("Tentando enviar dados com JWT forjado...");
        
        SensorData fakeData = SensorData.generateRandom(sensorId);
        
        // Passo 1 - Definir token JWT forjado
        String forgedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJTRU5TT1JfRkFLRSIsImlzcyI6IkZBS0UiLCJpYXQiOjE2MzAwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0.fakeSignature123";
        
        MessageUDP dataMsg = channel.buildEncryptedEnvelope("EDGE", MessageTypeUDP.DATA, fakeData.toJson(), forgedToken);
        if (dataMsg == null) {
            logger.error("Falha ao construir mensagem DATA");
            return;
        }
        
        channel.send(dataMsg, edgeHost, edgePort);
        logger.info("[RESULTADO] Mensagem enviada com JWT forjado");
        logger.info("[ESPERADO] Edge deve ignorar mensagem (token inválido)");
        
        // Passo 2 - Aguardar resposta para verificar comportamento
        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.info("[SUCESSO] Ataque BLOQUEADO - nenhuma resposta (silenciosamente ignorado)");
        } else {
            logger.warn("[ATENÇÃO] Resposta recebida: {}", packet.message().getType());
        }
    }

    // Ataque 3 - Mensagem adulterada (esperado: verificação de assinatura falha)
    private void attackTamperedMessage() {
        logger.info("Tentando enviar mensagem adulterada...");
        
        SensorData fakeData = SensorData.generateRandom(sensorId);
        MessageUDP dataMsg = channel.buildEncryptedEnvelope("EDGE", MessageTypeUDP.DATA, fakeData.toJson(), "fake-jwt-token");
        if (dataMsg == null) {
            logger.error("Falha ao construir mensagem DATA");
            return;
        }
        
        // Passo 1 - Adulterar payload cifrado (modificar alguns bytes)
        String originalPayload = dataMsg.getEncryptedPayload();
        String tamperedPayload = originalPayload.substring(0, 10) + "XXXX" + originalPayload.substring(14);
        
        // Passo 2 - Criar mensagem adulterada usando builder (manter tipo e assinatura original, mas payload corrompido)
        MessageUDP tamperedMsg = MessageUDP.builder()
                .type(dataMsg.getType())
                .senderId(sensorId)
                .encryptedPayload(tamperedPayload)
                .signature(dataMsg.getSignature()) // Mantém assinatura original (agora inválida)
                .jwtToken("fake-jwt-token")
                .build();
        
        channel.send(tamperedMsg, edgeHost, edgePort);
        logger.info("[RESULTADO] Mensagem adulterada enviada");
        logger.info("[ESPERADO] Edge deve rejeitar (assinatura inválida)");
        
        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.info("[SUCESSO] Ataque BLOQUEADO - nenhuma resposta (verificação de assinatura falhou)");
        } else {
            logger.warn("[ATENÇÃO] Resposta recebida: {}", packet.message().getType());
        }
    }

    // Ataque 4 - Dados sem autenticação (esperado: mensagem rejeitada)
    private void attackWithoutAuth() {
        logger.info("Tentando enviar dados sem autenticação prévia...");
        
        SensorData fakeData = SensorData.generateRandom(sensorId);
        // Enviar envelope mas sem JWT token válido (passando null ou string vazia)
        MessageUDP dataMsg = channel.buildEncryptedEnvelope("EDGE", MessageTypeUDP.DATA, fakeData.toJson(), null);
        if (dataMsg == null) {
            logger.error("Falha ao construir mensagem DATA");
            return;
        }
        
        // Passo 1 - Enviar mensagem sem token JWT
        channel.send(dataMsg, edgeHost, edgePort);
        logger.info("[RESULTADO] Mensagem enviada SEM token JWT");
        logger.info("[ESPERADO] Edge deve rejeitar (token ausente)");
        
        ReceivedPacket packet = channel.receive();
        if (packet == null) {
            logger.info("[SUCESSO] Ataque BLOQUEADO - nenhuma resposta (sem autenticação)");
        } else {
            logger.warn("[ATENÇÃO] Resposta recebida: {}", packet.message().getType());
        }
    }

    public static void main(String[] args) {
        String id = args.length > 0 ? args[0] : "MALICIOUS_SENSOR";
        String host = args.length > 1 ? args[1] : "localhost";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 4000;
        
        MaliciousSensor malicious = new MaliciousSensor(id, host, port);
        malicious.runAllAttacks();
    }
}
