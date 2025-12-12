package com.project.collector;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener UDP otimizado para evitar perda de pacotes.
 * 
 * Usa arquitetura produtor-consumidor:
 * - Thread principal: recebe pacotes UDP o mais rápido possível
 * - Thread pool: processa eventos em paralelo
 * - Fila: buffer entre recebimento e processamento
 */
public class UdpListener implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(UdpListener.class);
    
    // Tamanho do buffer de pacotes UDP
    private static final int PACKET_BUFFER_SIZE = 65535;
    
    // Tamanho do buffer de recebimento do socket (8MB para evitar drops)
    private static final int SOCKET_RECEIVE_BUFFER_SIZE = 8 * 1024 * 1024;
    
    // Tamanho da fila de eventos pendentes
    private static final int EVENT_QUEUE_CAPACITY = 10000;
    
    // Número de threads para processar eventos
    private static final int PROCESSOR_THREADS = 4;

    private final int port;
    private final Consumer<TraceEvent> eventHandler;
    private volatile boolean running = true;
    private DatagramSocket socket;
    
    // Fila de eventos para processamento assíncrono
    private final BlockingQueue<String> eventQueue = new ArrayBlockingQueue<>(EVENT_QUEUE_CAPACITY);
    
    // Thread pool para processar eventos
    private final ExecutorService processorPool = Executors.newFixedThreadPool(PROCESSOR_THREADS, r -> {
        Thread t = new Thread(r, "trace-processor");
        t.setDaemon(true);
        return t;
    });

    public UdpListener(int port, Consumer<TraceEvent> eventHandler) {
        this.port = port;
        this.eventHandler = eventHandler;
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            
            // Aumentar buffer de recebimento do socket para evitar drops
            try {
                socket.setReceiveBufferSize(SOCKET_RECEIVE_BUFFER_SIZE);
                int actualSize = socket.getReceiveBufferSize();
                logger.info("Buffer de recebimento UDP: {} bytes (solicitado: {} bytes)", 
                           actualSize, SOCKET_RECEIVE_BUFFER_SIZE);
            } catch (SocketException e) {
                logger.warn("Não foi possível aumentar buffer do socket: {}", e.getMessage());
            }
            
            logger.info("Listener UDP iniciado na porta {}", port);
            
            // Iniciar threads de processamento
            for (int i = 0; i < PROCESSOR_THREADS; i++) {
                processorPool.submit(this::processEvents);
            }

            byte[] buffer = new byte[PACKET_BUFFER_SIZE];

            // Loop de recebimento - apenas recebe e enfileira, não processa
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                
                // Tentar enfileirar sem bloquear
                if (!eventQueue.offer(json)) {
                    logger.warn("Fila de eventos cheia - descartando evento");
                }
            }
        } catch (Exception e) {
            if (running) {
                logger.error("Erro no listener UDP: {}", e.getMessage());
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
    
    /**
     * Processa eventos da fila (executado em threads separadas).
     */
    private void processEvents() {
        while (running) {
            try {
                // Aguarda evento na fila (com timeout para verificar running)
                String json = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (json == null) continue;
                
                logger.debug("Evento de trace recebido: {}", json);
                
                TraceEvent event = TraceEvent.fromJson(json);
                eventHandler.accept(event);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Falha ao processar evento de trace: {}", e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        
        // Fechar socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        // Parar pool de processadores
        processorPool.shutdown();
        try {
            if (!processorPool.awaitTermination(2, TimeUnit.SECONDS)) {
                processorPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            processorPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
