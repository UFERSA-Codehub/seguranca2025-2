package com.project.server.edge;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import com.project.server.auth.ServerAuth;
import com.project.server.edge.data.Cache;

public class ServerEdge implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("Edge");
    
    private final String name;
    private final int port;
    private final int idsPort;
    private final String discoveryHost;
    private final int discoveryPort;
    
    private volatile boolean running;
    private KeyManager keyManager;
    private SecureUDPChannel udpChannel;
    private ServerSocket tcpServerSocket;
    private ServerSocket idsServerSocket;
    private ExecutorService sensorThreadPool;
    private ExecutorService idsThreadPool;
    private JWT jwt;
    private Cache cache;
    
    // Registro de conexões ativas por IP (para TERMINATE do IDS)
    private final Map<String, SensorTcpHandler> activeConnections = new ConcurrentHashMap<>();
    
    // Blacklist de sensores (por sensor ID) - sensores bloqueados pelo IDS
    private final Set<String> sensorBlacklist = ConcurrentHashMap.newKeySet();
    
    // Cliente UDP para Discovery
    private UdpClient udpClient;
    
    // Informações do Datacenter descoberto
    private String datacenterHost;
    private int datacenterPort;
    private TcpClient datacenterClient;
    
    // Scheduler para flush periódico do cache e heartbeat
    private ScheduledExecutorService scheduler;
    private static final int FLUSH_INTERVAL_SECONDS = 30;
    private static final int RECONNECT_RETRY_SECONDS = 30;

    public ServerEdge(int port, String discoveryHost, int discoveryPort) {
        this.name = "EDGE";
        this.port = port;
        this.idsPort = port + 1;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
        this.running = false;
    }

    @Override
    public void start() {
        logger.info("[Edge] Iniciando na porta {}...", port);
        try {
            this.keyManager = new KeyManager();
            DatagramSocket udpSocket = new DatagramSocket();
            udpSocket.setSoTimeout(5000);
            this.udpChannel = new SecureUDPChannel(name, keyManager, udpSocket);
            this.tcpServerSocket = new ServerSocket(port);
            this.idsServerSocket = new ServerSocket(idsPort);
            this.sensorThreadPool = Executors.newFixedThreadPool(20);
            this.idsThreadPool = Executors.newFixedThreadPool(2);
            this.jwt = new JWT(ServerAuth.JWT_SECRET, "AuthServer");
            this.cache = new Cache();
            this.scheduler = Executors.newScheduledThreadPool(2);
            this.running = true;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        } catch (SocketException e) {
            logger.error("Erro ao abrir socket UDP: {}", e.getMessage());
            return;
        } catch (IOException e) {
            logger.error("Erro ao abrir socket TCP na porta {}: {}", port, e.getMessage());
            return;
        }

        // Inicializar cliente UDP para comunicação com Discovery
        this.udpClient = new UdpClient(name, udpChannel, discoveryHost, discoveryPort);
        udpClient.setScheduler(scheduler);

        // Registrar no Discovery
        if (!udpClient.handshake()) { 
            logger.warn("Falha no handshake com DISCOVERY");
            return;
        }

        if (!udpClient.register(port)) {
            logger.warn("Falha ao registrar no DISCOVERY");
            return;
        }

        // Iniciar heartbeat para Discovery
        udpClient.startHeartbeatScheduler();

        // Descobrir Datacenter disponível
        String datacenterInfo = udpClient.discoverDatacenter();
        if (datacenterInfo != null) {
            String[] parts = datacenterInfo.split(":");
            if (parts.length == 2) {
                this.datacenterHost = parts[0];
                this.datacenterPort = Integer.parseInt(parts[1]);
                this.datacenterClient = new TcpClient(name, datacenterHost, datacenterPort);
                logger.info("Datacenter descoberto: {}:{}", datacenterHost, datacenterPort);
            }
        } else {
        logger.warn("Datacenter não disponível no momento - tentará reconectar durante flush");
        }

        // Sempre iniciar flush scheduler (tem lógica de retry interna)
        startCacheFlushScheduler();

        // Iniciar listener para comandos do IDS
        startIdsListener();

        logger.info("[Edge] Iniciado na porta {} (TCP) e {} (IDS)", port, idsPort);
        
        // Loop principal de aceitação de conexões TCP de sensores
        while (running) {
            try {
                Socket clientSocket = tcpServerSocket.accept();
                String clientIp = extractIp(clientSocket);
                logger.info("Nova conexão de sensor: {}", clientSocket.getRemoteSocketAddress());
                SensorTcpHandler handler = new SensorTcpHandler(clientSocket, keyManager, jwt, cache, this);
                activeConnections.put(clientIp, handler);
                sensorThreadPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                logger.error("Erro ao aceitar conexão: {}", e.getMessage());
                }
            }
        }
    }

    private void startIdsListener() {
        idsThreadPool.submit(() -> {
            logger.info("Listener IDS iniciado na porta {}", idsPort);
            while (running) {
                try {
                    Socket idsSocket = idsServerSocket.accept();
                    logger.info("Conexão do IDS recebida: {}", idsSocket.getRemoteSocketAddress());
                    idsThreadPool.submit(new IdsCommandHandler(idsSocket, keyManager, this));
                } catch (IOException e) {
                    if (running) {
                        logger.error("Erro no listener IDS: {}", e.getMessage());
                    }
                }
            }
        });
    }

    public void terminateByIp(String ip) {
        SensorTcpHandler handler = activeConnections.remove(ip);
        if (handler != null) {
        logger.warn("Terminando conexão de IP malicioso: {}", ip);
            handler.forceClose();
        } else {
            logger.debug("IP {} não encontrado nas conexões ativas", ip);
        }
    }

    /**
     * Termina conexão e adiciona sensor à blacklist por sensor ID.
     * Usado quando IDS identifica um sensor malicioso pelo ID (não pelo IP).
     */
    public void terminateBySensorId(String sensorId) {
        // Adicionar à blacklist primeiro
        blacklistSensor(sensorId);
        
        // Procurar e fechar conexão ativa deste sensor
        for (Map.Entry<String, SensorTcpHandler> entry : activeConnections.entrySet()) {
            SensorTcpHandler handler = entry.getValue();
            if (sensorId.equals(handler.getPeerId())) {
                logger.warn("Terminando conexão de sensor malicioso: {}", sensorId);
                activeConnections.remove(entry.getKey());
                handler.forceClose();
                return;
            }
        }
        logger.debug("Sensor {} não encontrado nas conexões ativas (já pode ter desconectado)", sensorId);
    }

    /**
     * Adiciona um sensor à blacklist. Dados deste sensor serão rejeitados.
     */
    public void blacklistSensor(String sensorId) {
        if (sensorBlacklist.add(sensorId)) {
            logger.warn("Sensor '{}' adicionado à blacklist", sensorId);
        }
    }

    /**
     * Verifica se um sensor está na blacklist.
     */
    public boolean isSensorBlacklisted(String sensorId) {
        return sensorBlacklist.contains(sensorId);
    }

    /**
     * Remove um sensor da blacklist.
     */
    public void unblacklistSensor(String sensorId) {
        if (sensorBlacklist.remove(sensorId)) {
            logger.info("Sensor '{}' removido da blacklist", sensorId);
        }
    }

    public void unregisterConnection(String ip) {
        activeConnections.remove(ip);
        logger.debug("Conexão removida do registro: {}", ip);
    }

    private String extractIp(Socket socket) {
        String address = socket.getRemoteSocketAddress().toString();
        if (address.startsWith("/")) {
            address = address.substring(1);
        }
        int colonIndex = address.lastIndexOf(':');
        if (colonIndex > 0) {
            return address.substring(0, colonIndex);
        }
        return address;
    }

    private void startCacheFlushScheduler() {
        scheduler.scheduleAtFixedRate(
            this::flushToDatacenter,
            FLUSH_INTERVAL_SECONDS,  // delay inicial
            FLUSH_INTERVAL_SECONDS,  // período
            TimeUnit.SECONDS
        );
        logger.info("Scheduler de flush iniciado - intervalo: {}s", FLUSH_INTERVAL_SECONDS);
    }

    private void flushToDatacenter() {
        if (cache.getCount() == 0) {
            logger.debug("Cache vazio - nada para enviar ao Datacenter");
            return;
        }

        // Passo 1 - Tentar conectar ao Datacenter atual
        if (!ensureDatacenterConnection()) {
            logger.warn("Não foi possível conectar ao Datacenter - dados permanecem no cache");
            return;
        }

        List<Cache.CacheEntry> entries = cache.flush();
        if (entries.isEmpty()) {
            return;
        }

        // Passo 2 - Filtrar dados de sensores na blacklist (defesa em profundidade)
        int originalCount = entries.size();
        entries = entries.stream()
            .filter(e -> !sensorBlacklist.contains(e.sensorId()))
            .toList();
        
        int filteredCount = originalCount - entries.size();
        if (filteredCount > 0) {
            logger.warn("Filtrados {} registros de sensores na blacklist", filteredCount);
        }

        if (entries.isEmpty()) {
            logger.debug("Todos os registros eram de sensores na blacklist - nada a enviar");
            return;
        }

        logger.info("Enviando {} registros para o Datacenter...", entries.size());

        if (datacenterClient.sendBatch(entries)) {
            logger.info("Dados enviados com sucesso ao Datacenter");
        } else {
            logger.error("Falha ao enviar dados - restaurando no cache");
            for (Cache.CacheEntry entry : entries) {
                cache.store(entry.sensorId(), entry.data(), entry.isAlert(), entry.alertType());
            }
        }
    }

    private boolean ensureDatacenterConnection() {
        if (datacenterClient != null && datacenterClient.ensureConnected()) {
            return true;
        }

        logger.info("Tentando redescobrir Datacenter via Discovery...");

        while (running) {
            String datacenterInfo = udpClient.discoverDatacenter();
            if (datacenterInfo != null) {
                String[] parts = datacenterInfo.split(":");
                if (parts.length == 2) {
                    this.datacenterHost = parts[0];
                    this.datacenterPort = Integer.parseInt(parts[1]);
                    this.datacenterClient = new TcpClient(name, datacenterHost, datacenterPort);
                    
                    if (datacenterClient.ensureConnected()) {
                        logger.info("Reconectado ao Datacenter {}:{}", datacenterHost, datacenterPort);
                        return true;
                    }
                }
            }

            logger.warn("Datacenter não disponível - aguardando {}s para retentar...", RECONNECT_RETRY_SECONDS);
            try {
                Thread.sleep(RECONNECT_RETRY_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    public Cache getCache() {
        return cache;
    }

    @Override
    public void stop() {
        logger.info("[Edge] Parando...");
        this.running = false;
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (sensorThreadPool != null && !sensorThreadPool.isShutdown()) {
            sensorThreadPool.shutdown();
            try {
                if (!sensorThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    sensorThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                sensorThreadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (idsThreadPool != null && !idsThreadPool.isShutdown()) {
            idsThreadPool.shutdown();
            try {
                if (!idsThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    idsThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                idsThreadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (datacenterClient != null) {
            datacenterClient.disconnect();
        }
        
        try {
            if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
                tcpServerSocket.close();
            }
        } catch (IOException e) {
            logger.error("Erro ao fechar ServerSocket: {}", e.getMessage());
        }

        try {
            if (idsServerSocket != null && !idsServerSocket.isClosed()) {
                idsServerSocket.close();
            }
        } catch (IOException e) {
            logger.error("Erro ao fechar IDS ServerSocket: {}", e.getMessage());
        }
        
        if (udpChannel != null) {
            udpChannel.getSocket().close();
        }
        logger.info("[Edge] Parado");
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public String getName() { return name; }

    @Override
    public int getPort() { return port; }

    @Override
    public void showStatus() {
        logger.info("=== Status do Servidor Edge ===");
        logger.info("Nome: {} | Porta: {} | Status: {}", name, port, running ? "Em execução" : "Parado");
        logger.info("Dados no cache: {}", cache != null ? cache.getCount() : 0);
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        String discoveryHost = args.length > 1 ? args[1] : "localhost";
        int discoveryPort = args.length > 2 ? Integer.parseInt(args[2]) : 4000;
        
        ServerEdge server = new ServerEdge(port, discoveryHost, discoveryPort);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
