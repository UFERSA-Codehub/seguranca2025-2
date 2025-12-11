package com.project.collector;

import java.net.InetSocketAddress;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketBroadcaster extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketBroadcaster.class);

    public WebSocketBroadcaster(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("Client connected: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("Client disconnected: {} (code: {}, reason: {})", 
            conn.getRemoteSocketAddress(), code, reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug("Received message from client: {}", message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("WebSocket error: {}", ex.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server started on port {}", getPort());
    }

    public void broadcastEvent(TraceEvent event) {
        String json = event.toJson();
        logger.debug("Broadcasting event to {} clients", getConnections().size());
        broadcast(json);
    }
}
