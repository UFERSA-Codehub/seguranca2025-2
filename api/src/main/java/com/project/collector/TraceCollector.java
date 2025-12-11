package com.project.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceCollector {
    private static final Logger logger = LoggerFactory.getLogger(TraceCollector.class);

    private static final int UDP_PORT = 6000;
    private static final int WS_PORT = 6001;

    public static void main(String[] args) {
        logger.info("Starting Trace Collector...");

        WebSocketBroadcaster broadcaster = new WebSocketBroadcaster(WS_PORT);
        broadcaster.start();

        UdpListener udpListener = new UdpListener(UDP_PORT, broadcaster::broadcastEvent);
        Thread udpThread = new Thread(udpListener, "udp-listener");
        udpThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Trace Collector...");
            udpListener.stop();
            try {
                broadcaster.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        logger.info("Trace Collector running - UDP:{}, WebSocket:{}", UDP_PORT, WS_PORT);
    }
}
