package com.project.server.firewall;

import java.io.IOException;
import java.net.InetSocketAddress;
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

public class PacketFilter implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("PacketFilter");

    private static final String REVERSE_PROXY_HOST = "localhost";
    private static final String IDS_HOST = "localhost";
    private static final int IDS_PORT = 3002;

    private static final Map<Integer, Integer> PORT_MAPPING = Map.of(
        3000, 3001,   // AuthServer route
        3010, 3011,   // Edge route
        3020, 3021    // Datacenter route
    );

    private static final Map<Integer, String> SERVICE_NAMES = Map.of(
        3000, "AuthServer",
        3010, "Edge",
        3020, "Datacenter"
    );

    private final String name;
    private volatile boolean running;
    private KeyManager keyManager;
    private RuleEngine ruleEngine;
    private IdsClient idsClient;
    private ExecutorService threadPool;
    private final Map<Integer, ServerSocket> serverSockets;

    public PacketFilter() {
        this.name = "PacketFilter";
        this.running = false;
        this.serverSockets = new ConcurrentHashMap<>();
    }

    @Override
    public void start() {
        logger.info("Iniciando [Packet Filter]...");

        try {
            this.keyManager = new KeyManager();
            this.ruleEngine = new RuleEngine();
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
        for (int listenPort : PORT_MAPPING.keySet()) {
            threadPool.submit(() -> startListener(listenPort));
        }

        logger.info("[Packet Filter] iniciado - portas: {}", PORT_MAPPING.keySet());
    }

    private void startListener(int listenPort) {
        int targetPort = PORT_MAPPING.get(listenPort);
        String serviceName = SERVICE_NAMES.get(listenPort);

        try {
            ServerSocket serverSocket = new ServerSocket(listenPort);
            serverSockets.put(listenPort, serverSocket);
            logger.info("Listener iniciado na porta {} -> ReverseProxy:{} ({})", 
                       listenPort, targetPort, serviceName);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleConnection(clientSocket, targetPort, serviceName);
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

    private void handleConnection(Socket clientSocket, int targetPort, String serviceName) {
        String clientIp = extractIp(clientSocket);
        int clientPort = clientSocket.getPort();

        logger.info("Nova conexao de {} para {}", clientIp, serviceName);

        // Verificar regras
        RuleEngine.CheckResult result = ruleEngine.checkConnection(clientIp, clientSocket.getLocalPort());

        if (!result.allowed()) {
            logger.warn("Conexao bloqueada de {} - {}: {}", clientIp, result.alertType(), result.reason());

            // Enviar alerta ao IDS
            idsClient.sendAlert(clientIp, clientPort, serviceName, result.alertType(), result.reason());

            // Fechar conexao
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.debug("Erro ao fechar socket: {}", e.getMessage());
            }
            return;
        }

        // Conexao permitida - encaminhar para ReverseProxy
        try {
            Socket serverSocket = new Socket();
            serverSocket.connect(new InetSocketAddress(REVERSE_PROXY_HOST, targetPort), 5000);

            logger.debug("Forwarding {} -> ReverseProxy:{}", clientIp, targetPort);

            ConnectionForwarder forwarder = new ConnectionForwarder(clientSocket, serverSocket, clientIp, serviceName);
            threadPool.submit(forwarder);

        } catch (IOException e) {
            logger.error("Falha ao conectar ao ReverseProxy:{} - {}", targetPort, e.getMessage());
            try {
                clientSocket.close();
            } catch (IOException ex) {
                logger.debug("Erro ao fechar socket: {}", ex.getMessage());
            }
        }
    }

    private String extractIp(Socket socket) {
        InetSocketAddress addr = (InetSocketAddress) socket.getRemoteSocketAddress();
        return addr.getAddress().getHostAddress();
    }

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }

    @Override
    public void stop() {
        logger.info("Parando [Packet Filter]...");
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

        logger.info("[Packet Filter] parado.");
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public String getName() { return name; }

    @Override
    public int getPort() { return 3000; }

    @Override
    public void showStatus() {
        logger.info("=== Status do Packet Filter ===");
        logger.info("Status: {}", running ? "Em execucao" : "Parado");
        logger.info("Portas ativas: {}", PORT_MAPPING.keySet());
        logger.info("IPs na blacklist: {}", ruleEngine.getBlacklist().size());
        logger.info("Conexao com IDS: {}", idsClient.isConnected() ? "Ativa" : "Inativa");
    }

    public static void main(String[] args) {
        PacketFilter server = new PacketFilter();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
