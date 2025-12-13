package com.project.collector;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servidor WebSocket para broadcast de eventos de trace.
 * 
 * Acumula eventos em buffer e envia ordenados periodicamente.
 */
public class WebSocketBroadcaster extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketBroadcaster.class);
    private static final int MAX_STORED_EVENTS = 1000;
    
    // Intervalo de flush do buffer em milissegundos
    private static final long BUFFER_FLUSH_INTERVAL_MS = 150;

    private final ConcurrentLinkedDeque<TraceEvent> eventHistory = new ConcurrentLinkedDeque<>();
    
    // Buffer para acumular eventos antes de ordenar e enviar
    private final ConcurrentLinkedQueue<TraceEvent> eventBuffer = new ConcurrentLinkedQueue<>();
    
    // Scheduler para flush periódico do buffer
    private final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "trace-buffer-flush");
        t.setDaemon(true);
        return t;
    });
    
    // Comparador para ordenar eventos por timestamp, componentId e sequenceNumber
    private static final Comparator<TraceEvent> EVENT_COMPARATOR = Comparator
            .comparingLong(TraceEvent::timestamp)
            .thenComparing(e -> e.componentId() != null ? e.componentId() : "")
            .thenComparingLong(TraceEvent::sequenceNumber);

    public WebSocketBroadcaster(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true); // Permite reiniciar rapidamente sem esperar TIME_WAIT
        startBufferFlushScheduler();
    }
    
    private void startBufferFlushScheduler() {
        flushScheduler.scheduleAtFixedRate(
            this::flushBuffer,
            BUFFER_FLUSH_INTERVAL_MS,
            BUFFER_FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        logger.info("Scheduler de flush do buffer iniciado (intervalo: {}ms)", BUFFER_FLUSH_INTERVAL_MS);
    }
    
    private void flushBuffer() {
        if (eventBuffer.isEmpty()) {
            return;
        }
        
        // Coletar todos os eventos do buffer
        List<TraceEvent> eventsToSend = new ArrayList<>();
        TraceEvent event;
        while ((event = eventBuffer.poll()) != null) {
            eventsToSend.add(event);
        }
        
        if (eventsToSend.isEmpty()) {
            return;
        }
        
        // Ordenar por timestamp e sequenceNumber
        eventsToSend.sort(EVENT_COMPARATOR);
        
        // Adicionar ao histórico e fazer broadcast
        for (TraceEvent e : eventsToSend) {
            // Adicionar ao histórico
            eventHistory.addLast(e);
            
            // Remover eventos antigos se o buffer estiver cheio
            while (eventHistory.size() > MAX_STORED_EVENTS) {
                eventHistory.pollFirst();
            }
            
            // Broadcast para todos os clientes conectados
            String json = e.toJson();
            broadcast(json);
        }
        
        logger.debug("Flush de {} eventos (histórico: {})", eventsToSend.size(), eventHistory.size());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("Cliente conectado: {}", conn.getRemoteSocketAddress());
        
        // Enviar histórico de eventos para o novo cliente
        int historySize = eventHistory.size();
        if (historySize > 0) {
            logger.info("Enviando {} eventos armazenados para novo cliente", historySize);
            for (TraceEvent event : eventHistory) {
                conn.send(event.toJson());
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("Cliente desconectado: {} (código: {}, motivo: {})", 
            conn.getRemoteSocketAddress(), code, reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug("Mensagem recebida do cliente: {}", message);
        
        // Handle clear command from frontend
        if (message != null && message.contains("\"type\":\"clear\"")) {
            clearHistory();
            logger.info("Histórico de eventos limpo via comando do cliente");
        }
    }
    
    /**
     * Limpa o histórico de eventos armazenados.
     */
    public void clearHistory() {
        eventHistory.clear();
        eventBuffer.clear();
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("Erro WebSocket: {}", ex.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("Servidor WebSocket iniciado na porta {}", getPort());
    }

    public void broadcastEvent(TraceEvent event) {
        // Adicionar evento ao buffer (será processado no próximo flush)
        eventBuffer.add(event);
    }
    
    public void shutdown() {
        // Flush final antes de encerrar
        flushBuffer();
        flushScheduler.shutdown();
        try {
            if (!flushScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                flushScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            flushScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
