package com.project.server.datacenter;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.message.udp.MessageUDP;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;

public class UdpHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter.UdpHandler");

    private final SecureUDPChannel channel;
    private final Runnable reRegisterCallback;
    private volatile boolean running;

    public UdpHandler(SecureUDPChannel channel, Runnable reRegisterCallback) {
        this.channel = channel;
        this.reRegisterCallback = reRegisterCallback;
        this.running = true;
    }

    @Override
    public void run() {
        logger.info("UDP listener iniciado para mensagens do Discovery");
        
        while (running) {
            ReceivedPacket packet = channel.receive();
            if (packet != null) {
                handle(packet.message(), packet.address(), packet.port());
            }
        }
        
        logger.info("UDP listener encerrado");
    }

    public void handle(MessageUDP message, InetAddress address, int port) {
        switch (message.getType()) {
            case RE_REGISTER -> handleReRegister(message);
            default -> logger.debug("Mensagem UDP ignorada: {}", message.getType());
        }
    }

    private void handleReRegister(MessageUDP message) {
        String senderId = message.getSenderId();
        logger.warn("RE_REGISTER recebido de {} - Discovery pode ter reiniciado", senderId);

        // Passo 1 - Limpar sessão antiga com Discovery
        channel.clearPeerSession("DISCOVERY");

        // Passo 2 - Executar callback de re-registro
        if (reRegisterCallback != null) {
            reRegisterCallback.run();
        }
    }

    public void stop() {
        this.running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
