package com.project.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceCollector {
    private static final Logger logger = LoggerFactory.getLogger(TraceCollector.class);

    private static final int UDP_PORT = 6000;
    private static final int WS_PORT = 6001;

    public static void main(String[] args) {
        logger.info("Iniciando Trace Collector...");

        WebSocketBroadcaster broadcaster = new WebSocketBroadcaster(WS_PORT);
        broadcaster.start();

        UdpListener udpListener = new UdpListener(UDP_PORT, broadcaster::broadcastEvent);
        Thread udpThread = new Thread(udpListener, "udp-listener");
        udpThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Encerrando Trace Collector...");
            udpListener.stop();
            broadcaster.shutdown();
            try {
                broadcaster.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        logger.info("Trace Collector em execução - UDP:{}, WebSocket:{}", UDP_PORT, WS_PORT);
    }
}
