package com.project.server.auth;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.auth.JWT;
import com.project.crypto.KeyManager;
import com.project.network.SecureUDPChannel;
import com.project.server.IServer;

public class ServerAuth implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("ServerAuth");
    public static final String JWT_SECRET = "AuthServerSecretKey32BytesLong!!";
    private static final String JWT_ISSUER = "AuthServer";
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;

    private final String name;
    private final int port;
    private final String discoveryHost;
    private final int discoveryPort;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private ScheduledExecutorService scheduler;

    private KeyManager keyManager;
    private SecureUDPChannel discoveryChannel;
    private JWT jwt;
    private CredentialStore credentials;

    public ServerAuth(int port, String discoveryHost, int discoveryPort) {
        this.name = "AUTH";
        this.port = port;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
        this.running = false;
    }

    @Override
    public void start() {
        logger.info("Iniciando [Servidor de Autenticacao] na porta {}...", port);

        try {
            this.keyManager = new KeyManager();
            this.serverSocket = new ServerSocket(port);
            this.threadPool = Executors.newFixedThreadPool(10);
            this.scheduler = Executors.newScheduledThreadPool(1);
            this.jwt = new JWT(JWT_SECRET, JWT_ISSUER);
            this.credentials = new CredentialStore();
            this.running = true;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        } catch (IOException e) {
            logger.error("Erro ao abrir ServerSocket na porta {}: {}", port, e.getMessage());
            return;
        }

        if (!registerWithDiscovery()) {
            logger.error("Falha ao registrar no Discovery. Continuando sem registro...");
        }

        logger.info("[Servidor de Autenticacao] iniciado na porta {}", port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Nova conexao de {}", clientSocket.getRemoteSocketAddress());
                threadPool.submit(new TcpHandler(clientSocket, keyManager, jwt, credentials));
            } catch (IOException e) {
                if (running) {
                    logger.error("Erro ao aceitar conexao: {}", e.getMessage());
                }
            }
        }
    }

    private boolean registerWithDiscovery() {
        try {
            DatagramSocket udpSocket = new DatagramSocket();
            udpSocket.setSoTimeout(5000);
            this.discoveryChannel = new SecureUDPChannel(name, keyManager, udpSocket);
        } catch (SocketException e) {
            logger.error("Erro ao criar socket UDP: {}", e.getMessage());
            return false;
        }

        DiscoveryClient discoveryClient = new DiscoveryClient(name, discoveryChannel, discoveryHost, discoveryPort);
        
        if (!discoveryClient.handshake()) {
            logger.error("Falha no handshake com Discovery");
            return false;
        }

        if (!discoveryClient.register(port)) {
            logger.error("Falha ao registrar no Discovery");
            return false;
        }

        scheduler.scheduleAtFixedRate(
            () -> discoveryClient.sendHeartbeat(),
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        logger.info("Heartbeat scheduler iniciado - intervalo: {}s", HEARTBEAT_INTERVAL_SECONDS);

        return true;
    }

    @Override
    public void stop() {
        logger.info("Parando [Servidor de Autenticacao]...");
        this.running = false;

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }

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

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Erro ao fechar ServerSocket: {}", e.getMessage());
        }

        if (discoveryChannel != null && discoveryChannel.getSocket() != null) {
            discoveryChannel.getSocket().close();
        }

        logger.info("[Servidor de Autenticacao] parado.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void showStatus() {
        logger.info("=== Status do Servidor de Autenticacao ===");
        logger.info("Nome: {} | Porta: {} | Status: {}", name, port, running ? "Em execucao" : "Parado");
        if (credentials != null) {
            logger.info("Sensores: {} | Usuarios: {}", credentials.getSensorCount(), credentials.getUserCount());
        }
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 4001;
        String discoveryHost = args.length > 1 ? args[1] : "localhost";
        int discoveryPort = args.length > 2 ? Integer.parseInt(args[2]) : 4000;

        ServerAuth server = new ServerAuth(port, discoveryHost, discoveryPort);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
