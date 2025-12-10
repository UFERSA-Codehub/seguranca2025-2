package com.project.server.datacenter;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.KeyManager;
import com.project.network.SecureUDPChannel;
import com.project.server.IServer;
import com.project.server.datacenter.db.DataStore;
import com.project.server.datacenter.db.ReportService;

public class ServerDatacenter implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("Datacenter");

    private final String name;
    private final int tcpPort;
    private final int httpPort;
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
    private TcpHandler tcpHandler;
    private HttpHandler httpHandler;
    private BrowserHttpHandler browserHandler;
    private AuthClient authClient;

    public ServerDatacenter(int tcpPort, int httpPort, String discoveryHost, int discoveryPort) {
        this.name = "DATACENTER";
        this.tcpPort = tcpPort;
        this.httpPort = httpPort;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
        this.running = false;
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    @Override
    public void start() {
        logger.info("[Datacenter] Iniciando na porta TCP:{} e HTTP:{}...", tcpPort, httpPort);

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

        if (!udpClient.register(tcpPort, httpPort)) {
            logger.error("Falha no registro com Discovery. Encerrando.");
            return;
        }

        // Iniciar heartbeat para Discovery
        udpClient.startHeartbeatScheduler();

        // Inicializar serviços
        this.dataStore = new DataStore();
        this.reportService = new ReportService();
        
        // Iniciar TcpHandler para receber dados do Edge
        this.tcpHandler = new TcpHandler(tcpPort, dataStore, threadPool);
        threadPool.submit(tcpHandler);
        logger.info("TcpHandler iniciado na porta {}", tcpPort);

        // Inicializar AuthClient para delegação de autenticação HTTP
        this.authClient = new AuthClient(name);
        logger.info("AuthClient configurado para localhost:4001");

        // Iniciar HttpHandler para relatórios (com suporte a autenticação via AuthServer)
        this.httpHandler = new HttpHandler(httpPort, dataStore, reportService, keyManager);
        httpHandler.setAuthClient(authClient);
        httpHandler.start(threadPool);

        // Iniciar BrowserHttpHandler para testes via browser (porta 9091)
        int browserPort = httpPort + 1;
        this.browserHandler = new BrowserHttpHandler(browserPort, dataStore, reportService, authClient);
        browserHandler.start(threadPool);

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
        if (!udpClient.register(tcpPort, httpPort)) {
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
        
        if (httpHandler != null) {
            httpHandler.stop();
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
        logger.info("Nome: {} | TCP: {} | HTTP: {} | Status: {}", name, tcpPort, httpPort, running ? "Em execução" : "Parado");
        logger.info("Leituras: {} | Alertas: {}", 
                dataStore != null ? dataStore.getCount() : 0, 
                dataStore != null ? dataStore.getAlertCount() : 0);
    }

    public static void main(String[] args) {
        int tcpPort = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int httpPort = args.length > 1 ? Integer.parseInt(args[1]) : 9090;
        String discoveryHost = args.length > 2 ? args[2] : "localhost";
        int discoveryPort = args.length > 3 ? Integer.parseInt(args[3]) : 4000;

        ServerDatacenter server = new ServerDatacenter(tcpPort, httpPort, discoveryHost, discoveryPort);
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
