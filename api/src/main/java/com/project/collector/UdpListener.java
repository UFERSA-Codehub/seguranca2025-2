package com.project.collector;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdpListener implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(UdpListener.class);
    private static final int BUFFER_SIZE = 65535;

    private final int port;
    private final Consumer<TraceEvent> eventHandler;
    private volatile boolean running = true;
    private DatagramSocket socket;

    public UdpListener(int port, Consumer<TraceEvent> eventHandler) {
        this.port = port;
        this.eventHandler = eventHandler;
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            logger.info("UDP listener started on port {}", port);

            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                logger.debug("Received trace event: {}", json);

                try {
                    TraceEvent event = TraceEvent.fromJson(json);
                    eventHandler.accept(event);
                } catch (Exception e) {
                    logger.warn("Failed to parse trace event: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            if (running) {
                logger.error("UDP listener error: {}", e.getMessage());
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
