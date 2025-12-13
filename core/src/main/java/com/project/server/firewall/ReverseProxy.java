package com.project.server.firewall;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
import com.project.tracing.TraceEvent;
import com.project.tracing.TracerFactory;

public class ReverseProxy implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("Firewall.ReverseProxy");

    private static final String IDS_HOST = "localhost";
    private static final int IDS_PORT = 3002;

    private static final String DISCOVERY_HOST = "localhost";
    private static final int DISCOVERY_PORT = 4000;
    private static final int UDP_DISCOVERY_LISTEN_PORT = 3041;
    private static final int UDP_BUFFER_SIZE = 65535;

    private final String name;
    private final String internalHost;
    private final Map<Integer, ServerTarget> targetMapping;
    private volatile boolean running;
    private KeyManager keyManager;
    private IdsClient idsClient;
    private ExecutorService threadPool;
    private final Map<Integer, ServerSocket> serverSockets;
    private DatagramSocket udpSocket;

    public ReverseProxy() {
        this("localhost");
    }

    public ReverseProxy(String internalHost) {
        this.name = "ReverseProxy";
        this.internalHost = internalHost;
        this.running = false;
        this.serverSockets = new ConcurrentHashMap<>();
        
        // Build target mapping with configurable internal host
        // 3001 = External → Auth
        // 3011 = External → Edge
        // 3021 = External → Datacenter (TCP)
        // 3022 = Internal (Edge) → Datacenter (TCP)
        // 3031 = External → Datacenter (HTTP)
        this.targetMapping = Map.of(
            3001, new ServerTarget(internalHost, 4001, "AUTH"),
            3011, new ServerTarget(internalHost, 5000, "EDGE"),
            3021, new ServerTarget(internalHost, 8080, "DATACENTER"),
            3022, new ServerTarget(internalHost, 8080, "DATACENTER_EDGE"),
            3031, new ServerTarget(internalHost, 9090, "DATACENTER")
        );
    }

    @Override
    public void start() {
        logger.info("[ReverseProxy] Iniciando...");

        try {
            this.keyManager = new KeyManager();
            this.idsClient = new IdsClient(name, keyManager, IDS_HOST, IDS_PORT);
            this.threadPool = Executors.newFixedThreadPool(20);
            this.running = true;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        }

        if (!idsClient.connect()) {
            logger.warn("IDS nao disponivel - alertas serao perdidos");
        }

        for (int listenPort : targetMapping.keySet()) {
            threadPool.submit(() -> startListener(listenPort));
        }

        threadPool.submit(this::startUdpListener);

        logger.info("[ReverseProxy] Iniciado - TCP: {}, UDP: {}, Internal: {}", targetMapping.keySet(), UDP_DISCOVERY_LISTEN_PORT, internalHost);
    }

    private void startUdpListener() {
        try {
            udpSocket = new DatagramSocket(UDP_DISCOVERY_LISTEN_PORT);
            logger.info("UDP listener iniciado na porta {} -> Discovery:{}", 
                       UDP_DISCOVERY_LISTEN_PORT, DISCOVERY_PORT);

            byte[] buffer = new byte[UDP_BUFFER_SIZE];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    handleUdpPacket(packet);
                } catch (IOException e) {
                    if (running) {
                        logger.error("Erro ao receber pacote UDP: {}", e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            logger.error("Erro ao iniciar UDP listener na porta {}: {}", UDP_DISCOVERY_LISTEN_PORT, e.getMessage());
        }
    }

    private void handleUdpPacket(DatagramPacket clientPacket) {
        String clientIp = clientPacket.getAddress().getHostAddress();
        int clientPort = clientPacket.getPort();
        String clientAddr = clientIp + ":" + clientPort;

        TracerFactory.getTracer().trace(TraceEvent.create(
            "REVERSE_PROXY",
            "UDP",
            "RECEIVE",
            clientAddr,
            null,
            "DISCOVERY_REQUEST",
            null,
            null,
            clientAddr
        ));

        try (DatagramSocket forwardSocket = new DatagramSocket()) {
            forwardSocket.setSoTimeout(5000);

            byte[] data = new byte[clientPacket.getLength()];
            System.arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());

            InetAddress discoveryAddr = InetAddress.getByName(DISCOVERY_HOST);
            DatagramPacket forwardPacket = new DatagramPacket(data, data.length, discoveryAddr, DISCOVERY_PORT);
            forwardSocket.send(forwardPacket);

            TracerFactory.getTracer().trace(TraceEvent.create(
                "REVERSE_PROXY",
                "UDP",
                "SEND",
                DISCOVERY_HOST + ":" + DISCOVERY_PORT,
                null,
                "DISCOVERY_FORWARD",
                null,
                null,
                "DISCOVERY"
            ));

            byte[] responseBuffer = new byte[UDP_BUFFER_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            forwardSocket.receive(responsePacket);

            byte[] responseData = new byte[responsePacket.getLength()];
            System.arraycopy(responsePacket.getData(), responsePacket.getOffset(), responseData, 0, responsePacket.getLength());

            DatagramPacket clientResponse = new DatagramPacket(
                responseData, 
                responseData.length, 
                clientPacket.getAddress(), 
                clientPacket.getPort()
            );
            udpSocket.send(clientResponse);

            TracerFactory.getTracer().trace(TraceEvent.create(
                "REVERSE_PROXY",
                "UDP",
                "SEND",
                clientAddr,
                null,
                "DISCOVERY_RESPONSE",
                null,
                null,
                clientAddr
            ));

            logger.info("UDP forwarded {}:{} -> Discovery:{}", clientIp, clientPort, DISCOVERY_PORT);

        } catch (IOException e) {
            logger.error("Erro ao encaminhar UDP para Discovery: {}", e.getMessage());
        }
    }

    private void startListener(int listenPort) {
        ServerTarget target = targetMapping.get(listenPort);

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
            idsClient
        );

        threadPool.submit(handler);
    }

    @Override
    public void stop() {
        logger.info("[ReverseProxy] Parando...");
        this.running = false;

        if (idsClient != null) {
            idsClient.disconnect();
        }

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
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

        logger.info("[ReverseProxy] Parado");
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
        logger.info("Portas TCP: {}", targetMapping.keySet());
        logger.info("Porta UDP (Discovery): {}", UDP_DISCOVERY_LISTEN_PORT);
        logger.info("Conexao com IDS: {}", idsClient.isConnected() ? "Ativa" : "Inativa");
    }

    public static void main(String[] args) {
        String internalHost = args.length > 0 ? args[0] : "localhost";
        
        ReverseProxy server = new ReverseProxy(internalHost);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    public record ServerTarget(String host, int port, String serviceName) {}
}
