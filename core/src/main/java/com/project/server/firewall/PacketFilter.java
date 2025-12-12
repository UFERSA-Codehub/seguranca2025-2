package com.project.server.firewall;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
import com.project.tracing.TraceEvent;
import com.project.tracing.TracerFactory;

public class PacketFilter implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("Firewall.PacketFilter");

    private static final String REVERSE_PROXY_HOST = "localhost";
    private static final String IDS_HOST = "localhost";
    private static final int IDS_PORT = 3002;

    private static final String DISCOVERY_HOST = "localhost";
    private static final int DISCOVERY_PORT = 4000;
    private static final int UDP_DISCOVERY_LISTEN_PORT = 3040;
    private static final int UDP_BUFFER_SIZE = 65535;

    private static final Map<Integer, Integer> PORT_MAPPING = Map.of(
        3000, 3001,
        3005, -1,
        3010, 3011,
        3020, 3021,
        3030, 3031
    );

    private static final Map<Integer, String> SERVICE_NAMES = Map.of(
        3000, "AuthServer",
        3005, "Honeypot",
        3010, "Edge",
        3020, "Datacenter",
        3030, "DatacenterCLI"
    );

    private final String name;
    private volatile boolean running;
    private KeyManager keyManager;
    private RuleEngine ruleEngine;
    private IdsClient idsClient;
    private ExecutorService threadPool;
    private final Map<Integer, ServerSocket> serverSockets;
    private DatagramSocket udpSocket;

    public PacketFilter() {
        this.name = "PacketFilter";
        this.running = false;
        this.serverSockets = new ConcurrentHashMap<>();
    }

    @Override
    public void start() {
        logger.info("[PacketFilter] Iniciando...");
        logger.info("[PacketFilter] Tracing habilitado: {}", TracerFactory.isEnabled());

        try {
            this.keyManager = new KeyManager();
            this.ruleEngine = new RuleEngine();
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

        for (int listenPort : PORT_MAPPING.keySet()) {
            threadPool.submit(() -> startListener(listenPort));
        }

        threadPool.submit(this::startUdpListener);

        logger.info("[PacketFilter] Iniciado - TCP: {}, UDP: {}", PORT_MAPPING.keySet(), UDP_DISCOVERY_LISTEN_PORT);
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
            "PACKET_FILTER",
            "UDP",
            "RECEIVE",
            clientAddr,
            null,
            "DISCOVERY_REQUEST",
            null,
            null,
            clientAddr
        ));

        RuleEngine.CheckResult result = ruleEngine.checkConnection(clientIp, UDP_DISCOVERY_LISTEN_PORT);

        if (!result.allowed()) {
            logger.warn("UDP bloqueado de {} - {}: {}", clientIp, result.alertType(), result.reason());
            idsClient.sendAlert(clientIp, clientPort, "Discovery", result.alertType(), result.reason());
            return;
        }

        try (DatagramSocket forwardSocket = new DatagramSocket()) {
            forwardSocket.setSoTimeout(5000);

            byte[] data = new byte[clientPacket.getLength()];
            System.arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());

            InetAddress discoveryAddr = InetAddress.getByName(DISCOVERY_HOST);
            DatagramPacket forwardPacket = new DatagramPacket(data, data.length, discoveryAddr, DISCOVERY_PORT);
            forwardSocket.send(forwardPacket);

            TracerFactory.getTracer().trace(TraceEvent.create(
                "PACKET_FILTER",
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
                "PACKET_FILTER",
                "UDP",
                "SEND",
                clientAddr,
                null,
                "DISCOVERY_RESPONSE",
                null,
                null,
                clientAddr
            ));

        } catch (IOException e) {
            logger.error("Erro ao encaminhar UDP para Discovery: {}", e.getMessage());
        }
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
                    handleConnection(clientSocket, listenPort, targetPort, serviceName);
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

    private void handleConnection(Socket clientSocket, int listenPort, int targetPort, String serviceName) {
        String clientIp = extractIp(clientSocket);
        int clientPort = clientSocket.getPort();
        String clientAddr = clientIp + ":" + clientPort;

        logger.info("Nova conexao de {} para {}", clientIp, serviceName);

        TracerFactory.getTracer().trace(TraceEvent.create(
            "PACKET_FILTER",
            "TCP",
            "RECEIVE",
            clientAddr,
            null,
            "CONNECTION",
            null,
            null,
            clientAddr
        ));

        RuleEngine.CheckResult result = ruleEngine.checkConnection(clientIp, listenPort);

        if (!result.allowed()) {
            logger.warn("Conexao bloqueada de {} - {}: {}", clientIp, result.alertType(), result.reason());
            idsClient.sendAlert(clientIp, clientPort, serviceName, result.alertType(), result.reason());
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.debug("Erro ao fechar socket: {}", e.getMessage());
            }
            return;
        }

        try {
            Socket serverSocket = new Socket();
            serverSocket.connect(new InetSocketAddress(REVERSE_PROXY_HOST, targetPort), 5000);

            logger.info("Forwarding {} -> ReverseProxy:{}", clientIp, targetPort);

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
        logger.info("[PacketFilter] Parando...");
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

        logger.info("[PacketFilter] Parado");
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
        logger.info("Portas TCP: {}", PORT_MAPPING.keySet());
        logger.info("Porta UDP (Discovery): {}", UDP_DISCOVERY_LISTEN_PORT);
        logger.info("IPs na blacklist: {}", ruleEngine.getBlacklist().size());
        logger.info("Conexao com IDS: {}", idsClient.isConnected() ? "Ativa" : "Inativa");
    }

    public static void main(String[] args) {
        PacketFilter server = new PacketFilter();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
