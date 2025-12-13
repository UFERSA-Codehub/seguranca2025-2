package com.project.server.datacenter;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.auth.JWT;
import com.project.crypto.KeyManager;
import com.project.network.SecureUDPChannel;
import com.project.server.IServer;
import com.project.server.auth.ServerAuth;
import com.project.server.datacenter.db.DataStore;
import com.project.server.datacenter.db.ReportService;

public class ServerDatacenter implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter");

    private final String name;
    private final int tcpPort;          // Porta para conexões do Edge (8080)
    private final int clientTcpPort;    // Porta para conexões do CLI client (9090)
    private final String discoveryHost;
    private final int discoveryPort;

    private volatile boolean running;
    private SecureUDPChannel udpChannel;
    private KeyManager keyManager;

    private ExecutorService threadPool;
    
    // Handlers e clientes do Datacenter
    private UdpClient udpClient;
    private UdpHandler udpHandler;
    private DataStore dataStore;
    private ReportService reportService;
    private TcpHandler tcpHandler;              // Edge connections
    private ClientTcpHandler clientTcpHandler;  // CLI client connections
    private BrowserHttpHandler browserHandler;  // Browser HTTP access
    private AuthClient authClient;

    public ServerDatacenter(int tcpPort, int clientTcpPort, String discoveryHost, int discoveryPort) {
        this.name = "DATACENTER";
        this.tcpPort = tcpPort;
        this.clientTcpPort = clientTcpPort;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
        this.running = false;
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    @Override
    public void start() {
        logger.info("[Datacenter] Iniciando na porta TCP:{} e ClientTCP:{}...", tcpPort, clientTcpPort);

        try {
            this.keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket();
            this.udpChannel = new SecureUDPChannel(name, keyManager, socket);
            this.running = true;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        } catch (SocketException e) {
            logger.error("Erro ao abrir socket UDP {}: {}", name, e.getMessage());
            return;
        }

        // Inicializar cliente UDP para comunicação com Discovery
        this.udpClient = new UdpClient(name, udpChannel, discoveryHost, discoveryPort);

        if (!udpClient.handshake()) {
            logger.error("Falha no handshake com Discovery. Encerrando.");
            return;
        }

        if (!udpClient.register(tcpPort, clientTcpPort)) {
            logger.error("Falha no registro com Discovery. Encerrando.");
            return;
        }

        // Iniciar heartbeat para Discovery
        udpClient.startHeartbeatScheduler();

        // Inicializar serviços
        this.dataStore = new DataStore();
        this.reportService = new ReportService();
        
        // Iniciar TcpHandler para receber dados do Edge (usa KeyManager compartilhado)
        this.tcpHandler = new TcpHandler(tcpPort, dataStore, threadPool, keyManager);
        threadPool.submit(tcpHandler);
        logger.info("TcpHandler (Edge) iniciado na porta {}", tcpPort);

        // Inicializar AuthClient para delegação de autenticação (browser)
        this.authClient = new AuthClient(name);
        logger.info("AuthClient configurado para localhost:4001");

        // Iniciar ClientTcpHandler para conexões do CLI client (porta 9090)
        JWT jwt = new JWT(ServerAuth.JWT_SECRET, "AuthServer");
        this.clientTcpHandler = new ClientTcpHandler(clientTcpPort, dataStore, reportService, threadPool, keyManager, jwt);
        threadPool.submit(clientTcpHandler);
        logger.info("ClientTcpHandler (CLI) iniciado na porta {}", clientTcpPort);

        // Iniciar BrowserHttpHandler para acesso via browser (porta 9091)
        int browserPort = clientTcpPort + 1;
        this.browserHandler = new BrowserHttpHandler(browserPort, dataStore, reportService, authClient);
        browserHandler.start(threadPool);
        logger.info("BrowserHttpHandler iniciado na porta {}", browserPort);

        // Iniciar UdpHandler para mensagens do Discovery (ex: RE_REGISTER)
        this.udpHandler = new UdpHandler(udpChannel, this::performReRegister);
        threadPool.submit(udpHandler);

        logger.info("[Datacenter] Iniciado");
    }

    private void performReRegister() {
        // Re-fazer handshake
        if (!udpClient.handshake()) {
            logger.error("Falha no re-handshake com Discovery");
            return;
        }

        // Re-registrar
        if (!udpClient.register(tcpPort, clientTcpPort)) {
            logger.error("Falha no re-registro com Discovery");
            return;
        }

        logger.info("Re-registro com Discovery concluído");
    }

    @Override
    public void stop() {
        logger.info("[Datacenter] Parando...");
        this.running = false;

        // Parar cliente UDP (heartbeat scheduler)
        if (udpClient != null) {
            udpClient.stop();
        }

        // Parar handlers
        if (udpHandler != null) {
            udpHandler.stop();
        }

        if (tcpHandler != null) {
            tcpHandler.stop();
        }
        
        if (clientTcpHandler != null) {
            clientTcpHandler.stop();
        }

        if (browserHandler != null) {
            browserHandler.stop();
        }

        if (authClient != null) {
            authClient.disconnect();
        }
        
        // Parar thread pool
        threadPool.shutdown();

        logger.info("[Datacenter] Parado");
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public String getName() { return name; }

    @Override
    public int getPort() { return tcpPort; }

    @Override
    public void showStatus() {
        logger.info("=== Status do Servidor Datacenter ===");
        logger.info("Nome: {} | EdgeTCP: {} | ClientTCP: {} | Status: {}", name, tcpPort, clientTcpPort, running ? "Em execução" : "Parado");
        logger.info("Leituras: {} | Alertas: {}", 
                dataStore != null ? dataStore.getCount() : 0, 
                dataStore != null ? dataStore.getAlertCount() : 0);
    }

    public static void main(String[] args) {
        int tcpPort = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int clientTcpPort = args.length > 1 ? Integer.parseInt(args[1]) : 9090;
        String discoveryHost = args.length > 2 ? args[2] : "localhost";
        int discoveryPort = args.length > 3 ? Integer.parseInt(args[3]) : 3041;

        ServerDatacenter server = new ServerDatacenter(tcpPort, clientTcpPort, discoveryHost, discoveryPort);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();

        // Manter thread principal ativa
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
