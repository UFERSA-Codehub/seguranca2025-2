package com.project.tracing;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdpTracer implements IMessageTracer {
    private static final Logger logger = LoggerFactory.getLogger("Tracing.UdpTracer");
    private static final String COLLECTOR_HOST = "localhost";
    private static final int COLLECTOR_PORT = 6000;

    private final DatagramSocket socket;
    private final InetAddress collectorAddress;

    public UdpTracer() {
        DatagramSocket tempSocket = null;
        InetAddress tempAddress = null;
        
        try {
            tempSocket = new DatagramSocket();
            tempAddress = InetAddress.getByName(COLLECTOR_HOST);
            logger.info("UdpTracer inicializado - enviando para {}:{}", COLLECTOR_HOST, COLLECTOR_PORT);
        } catch (SocketException e) {
            logger.error("Erro ao criar socket UDP para tracing: {}", e.getMessage());
        } catch (UnknownHostException e) {
            logger.error("Host do collector desconhecido: {}", e.getMessage());
        }
        
        this.socket = tempSocket;
        this.collectorAddress = tempAddress;
    }

    @Override
    public void trace(TraceEvent event) {
        if (socket == null || collectorAddress == null) {
            return;
        }

        try {
            String json = event.toJson();
            byte[] data = json.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, collectorAddress, COLLECTOR_PORT);
            socket.send(packet);
            
            logger.debug("[TRACE] {} {} {} -> {} | tipo={}", 
                event.protocol(), 
                event.direction(), 
                event.componentId(), 
                event.peerId() != null ? event.peerId() : event.remoteAddress(),
                event.messageType()
            );
        } catch (IOException e) {
            logger.warn("Erro ao enviar trace: {}", e.getMessage());
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.debug("UdpTracer socket fechado");
        }
    }
}
