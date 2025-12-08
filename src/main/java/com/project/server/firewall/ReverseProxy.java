package com.project.server.firewall;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.KeyManager;
import com.project.server.IServer;

public class ReverseProxy implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("ReverseProxy");

    private static final String IDS_HOST = "localhost";
    private static final int IDS_PORT = 3002;

    private static final Map<Integer, ServerTarget> TARGET_MAPPING = Map.of(
        3001, new ServerTarget("localhost", 4001, "AUTH", false),
        3011, new ServerTarget("localhost", 5000, "EDGE", false),
        3021, new ServerTarget("localhost", 9090, "DATACENTER", true)
    );

    private final String name;
    private volatile boolean running;
    private KeyManager keyManager;
    private IdsClient idsClient;
    private ExecutorService threadPool;
    private final Map<Integer, ServerSocket> serverSockets;

    public ReverseProxy() {
        this.name = "ReverseProxy";
        this.running = false;
        this.serverSockets = new ConcurrentHashMap<>();
    }

    @Override
    public void start() {
        logger.info("Iniciando [Reverse Proxy]...");

        try {
            this.keyManager = new KeyManager();
            this.idsClient = new IdsClient(name, keyManager, IDS_HOST, IDS_PORT);
            this.threadPool = Executors.newCachedThreadPool();
            this.running = true;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        }

        // Conectar ao IDS
        if (!idsClient.connect()) {
            logger.warn("IDS nao disponivel - alertas serao perdidos");
        }

        // Iniciar listener para cada porta
        for (int listenPort : TARGET_MAPPING.keySet()) {
            threadPool.submit(() -> startListener(listenPort));
        }

        logger.info("[Reverse Proxy] iniciado - portas: {}", TARGET_MAPPING.keySet());
    }

    private void startListener(int listenPort) {
        ServerTarget target = TARGET_MAPPING.get(listenPort);

        try {
            ServerSocket serverSocket = new ServerSocket(listenPort);
            serverSockets.put(listenPort, serverSocket);
            logger.info("Listener iniciado na porta {} -> {}:{} ({})", 
                       listenPort, target.host(), target.port(), target.serviceName());

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleConnection(clientSocket, target);
                } catch (IOException e) {
                    if (running) {
                        logger.error("Erro ao aceitar conexao na porta {}: {}", listenPort, e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            logger.error("Erro ao iniciar listener na porta {}: {}", listenPort, e.getMessage());
        }
    }

    private void handleConnection(Socket clientSocket, ServerTarget target) {
        logger.info("Nova conexao para {} via porta {}", 
                   target.serviceName(), clientSocket.getLocalPort());

        ProxyHandler handler = new ProxyHandler(
            clientSocket,
            target.host(),
            target.port(),
            target.serviceName(),
            keyManager,
            idsClient,
            target.isHttp()
        );

        threadPool.submit(handler);
    }

    @Override
    public void stop() {
        logger.info("Parando [Reverse Proxy]...");
        this.running = false;

        if (idsClient != null) {
            idsClient.disconnect();
        }

        for (ServerSocket ss : serverSockets.values()) {
            try {
                ss.close();
            } catch (IOException e) {
                logger.debug("Erro ao fechar ServerSocket: {}", e.getMessage());
            }
        }
        serverSockets.clear();

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("[Reverse Proxy] parado.");
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public String getName() { return name; }

    @Override
    public int getPort() { return 3001; }

    @Override
    public void showStatus() {
        logger.info("=== Status do Reverse Proxy ===");
        logger.info("Status: {}", running ? "Em execucao" : "Parado");
        logger.info("Portas ativas: {}", TARGET_MAPPING.keySet());
        logger.info("Conexao com IDS: {}", idsClient.isConnected() ? "Ativa" : "Inativa");
    }

    public static void main(String[] args) {
        ReverseProxy server = new ReverseProxy();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    public record ServerTarget(String host, int port, String serviceName, boolean isHttp) {}
}
