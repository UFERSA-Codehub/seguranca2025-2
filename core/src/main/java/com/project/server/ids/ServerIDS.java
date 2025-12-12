package com.project.server.ids;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.message.tcp.MessageTCP;
import com.project.message.tcp.MessageTypeTCP;
import com.project.network.SecureTCPChannel;
import com.project.server.IServer;

public class ServerIDS implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("IDS");

    private static final int DEFAULT_PORT = 3002;
    private static final int EDGE_COMMAND_PORT = 5001;
    private static final String EDGE_HOST = "localhost";

    private final String name;
    private final int port;
    
    private volatile boolean running;
    private KeyManager keyManager;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private AlertStore alertStore;
    private ReportGenerator reportGenerator;

    private Socket edgeSocket;
    private SecureTCPChannel edgeChannel;
    private volatile boolean edgeConnected;

    public ServerIDS(int port) {
        this.name = "IDS";
        this.port = port;
        this.running = false;
        this.edgeConnected = false;
    }

    @Override
    public void start() {
        logger.info("[IDS] Iniciando na porta {}...", port);
        
        try {
            this.keyManager = new KeyManager();
            this.serverSocket = new ServerSocket(port);
            this.threadPool = Executors.newFixedThreadPool(10);
            this.alertStore = new AlertStore();
            this.reportGenerator = new ReportGenerator(alertStore);
            this.running = true;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        } catch (IOException e) {
            logger.error("Erro ao abrir socket TCP na porta {}: {}", port, e.getMessage());
            return;
        }

        logger.info("[IDS] Iniciado na porta {} (TCP)", port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Nova conexao de firewall: {}", clientSocket.getRemoteSocketAddress());
                threadPool.submit(new TcpHandler(clientSocket, keyManager, alertStore, this));
            } catch (IOException e) {
                if (running) {
                    logger.error("Erro ao aceitar conexao: {}", e.getMessage());
                }
            }
        }
    }

    public void sendTerminateToEdge(String targetIp) {
        sendTerminateToEdge(targetIp, null);
    }

    public void sendTerminateToEdge(String targetIp, String sensorId) {
        if (!ensureEdgeConnection()) {
            logger.error("Nao foi possivel conectar ao Edge para enviar TERMINATE");
            return;
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("targetIp", targetIp);
            payload.addProperty("reason", "Limite de alertas excedido");
            if (sensorId != null) {
                payload.addProperty("sensorId", sensorId);
            }

            MessageTCP terminateMsg = edgeChannel.buildEncrypted("EDGE", MessageTypeTCP.TERMINATE, payload.toString());
            if (terminateMsg != null) {
                edgeChannel.send(terminateMsg);
                String target = sensorId != null ? "sensor " + sensorId : "IP " + targetIp;
                logger.info("TERMINATE enviado para Edge - alvo: {}", target);

                // Tracing feito apenas no RECEIVE (possui payload cifrado e decifrado)

                MessageTCP response = edgeChannel.receive();
                if (response != null && edgeChannel.verify(response)) {
                    logger.info("Edge confirmou TERMINATE para {}", target);
                }
            }
        } catch (Exception e) {
            logger.error("Erro ao enviar TERMINATE: {}", e.getMessage());
            disconnectFromEdge();
        }
    }

    private synchronized boolean ensureEdgeConnection() {
        if (edgeConnected && edgeSocket != null && !edgeSocket.isClosed()) {
            return true;
        }

        logger.debug("Conectando ao Edge em {}:{}...", EDGE_HOST, EDGE_COMMAND_PORT);

        try {
            edgeSocket = new Socket(EDGE_HOST, EDGE_COMMAND_PORT);
            edgeSocket.setSoTimeout(10000);
            edgeChannel = new SecureTCPChannel("IDS", keyManager, edgeSocket);
            // Definir tracePeerId antes de qualquer send/receive para rastreamento correto
            edgeChannel.setTracePeerId("EDGE");

            // Handshake com Edge
            MessageTCP hello = edgeChannel.buildHello();
            edgeChannel.send(hello);

            MessageTCP challenge = edgeChannel.receive();
            if (challenge == null || challenge.getType() != MessageTypeTCP.CHALLENGE) {
                logger.error("Esperava CHALLENGE do Edge, recebeu: {}", 
                           challenge != null ? challenge.getType() : "null");
                disconnectFromEdge();
                return false;
            }

            if (!edgeChannel.handleChallenge(challenge)) {
                logger.error("Falha ao processar CHALLENGE do Edge");
                disconnectFromEdge();
                return false;
            }

            edgeConnected = true;
            logger.debug("Conectado ao Edge com sucesso");
            return true;

        } catch (IOException e) {
            logger.error("Falha ao conectar ao Edge: {}", e.getMessage());
            disconnectFromEdge();
            return false;
        }
    }

    private synchronized void disconnectFromEdge() {
        edgeConnected = false;
        if (edgeChannel != null) {
            edgeChannel.close();
            edgeChannel = null;
        }
        if (edgeSocket != null) {
            try {
                edgeSocket.close();
            } catch (IOException e) {
                logger.debug("Erro ao fechar socket do Edge: {}", e.getMessage());
            }
            edgeSocket = null;
        }
    }

    public String generateReport() {
        return reportGenerator.generateFullReport();
    }

    public String generateSummaryReport() {
        return reportGenerator.generateSummaryReport();
    }

    public String generateReportByIp(String ip) {
        return reportGenerator.generateReportByIp(ip);
    }

    public AlertStore getAlertStore() {
        return alertStore;
    }

    @Override
    public void stop() {
        logger.info("[IDS] Parando...");
        this.running = false;

        disconnectFromEdge();

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

        // Gerar relatório final
        logger.info("=== RELATORIO FINAL DO IDS ===");
        logger.info(reportGenerator.generateSummaryReport());

        logger.info("[IDS] Parado");
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public String getName() { return name; }

    @Override
    public int getPort() { return port; }

    @Override
    public void showStatus() {
        logger.info("=== Status do Servidor IDS ===");
        logger.info("Nome: {} | Porta: {} | Status: {}", name, port, running ? "Em execucao" : "Parado");
        logger.info("Total de alertas: {}", alertStore.getTotalCount());
        logger.info("IPs distintos: {}", alertStore.getDistinctIps().size());
        logger.info("Conexao com Edge: {}", edgeConnected ? "Ativa" : "Inativa");
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        ServerIDS server = new ServerIDS(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
