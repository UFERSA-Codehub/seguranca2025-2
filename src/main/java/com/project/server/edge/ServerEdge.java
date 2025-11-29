package com.project.server.edge;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.auth.JWT;
import com.project.crypto.KeyManager;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;
import com.project.server.IServer;
import com.project.server.edge.data.Cache;

public class ServerEdge implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("ServerEdge");
    private static final String JWT_SECRET = "EdgeServerSecretKeyForJWTSigning32B"; // 32 bytes for HS256
    
    private final String name;
    private final int port;
    private final String discoveryHost;
    private final int discoveryPort;
    
    private volatile boolean running;
    private SecureUDPChannel channel;
    private JWT jwt;
    private Cache cache;
    
    // Cliente e handler UDP
    private UdpClient udpClient;
    private UdpHandler udpHandler;
    
    // Armazena credenciais: sensorId -> hash da senha
    private final Map<String, String> credentialStore;
    
    // Sessões ativas: sensorId -> token JWT
    private final Map<String, String> activeSessions;
    
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
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
        this.running = false;
        this.credentialStore = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        
        // Pré-registrar sensores de teste
        registerSensor("SENSOR_001", "senha123");
        registerSensor("SENSOR_002", "senha456");
        registerSensor("SENSOR_003", "senha789");
        registerSensor("SENSOR_004", "senha321");
    }

    @Override
    public void start() {
        logger.info("Iniciando [Servidor Edge] na porta {}...", port);
        try {
            KeyManager keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket(port);
            this.channel = new SecureUDPChannel(name, keyManager, socket);
            this.jwt = new JWT(JWT_SECRET, name);
            this.cache = new Cache();
            this.scheduler = Executors.newScheduledThreadPool(2);
            this.running = true;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        } catch (SocketException e) {
            logger.error("Erro ao abrir socket na porta {}: {}", port, e.getMessage());
            return;
        }

        // Inicializar cliente UDP para comunicação com Discovery
        this.udpClient = new UdpClient(name, channel, discoveryHost, discoveryPort);
        udpClient.setScheduler(scheduler);

        // Inicializar UDP handler com callback de re-registro
        this.udpHandler = new UdpHandler(
            channel,
            jwt,
            cache,
            credentialStore,
            activeSessions,
            this::performReRegister
        );

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

        logger.info("[Servidor Edge] iniciado na porta {}", port);
        
        // Loop principal de recebimento de mensagens
        while (running) {
            ReceivedPacket packet = channel.receive();
            if (packet != null) {
                udpHandler.handle(packet.message(), packet.address(), packet.port());
            }
        }
    }

    private void performReRegister() {
        // Re-fazer handshake
        if (!udpClient.handshake()) {
            logger.error("Falha no re-handshake com Discovery");
            return;
        }

        // Re-registrar
        if (!udpClient.register(port)) {
            logger.error("Falha no re-registro com Discovery");
            return;
        }

        logger.info("Re-registro com Discovery concluído com sucesso");
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
        // Passo 1 - Se já temos cliente e está conectado, ok
        if (datacenterClient != null && datacenterClient.ensureConnected()) {
            return true;
        }

        // Passo 2 - Tentar redescobrir Datacenter via Discovery
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

            // Passo 3 - Discovery não encontrou ou conexão falhou, aguardar e retentar
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

    public void registerSensor(String sensorId, String password) {
        credentialStore.put(sensorId, password);
        logger.info("Sensor registrado: {}", sensorId);
    }

    public Cache getCache() {
        return cache;
    }

    @Override
    public void stop() {
        logger.info("Parando [Servidor Edge]...");
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
        
        if (datacenterClient != null) {
            datacenterClient.disconnect();
        }
        
        if (channel != null) {
            channel.getSocket().close();
        }
        logger.info("[Servidor Edge] parado.");
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
        logger.info("Sensores registrados: {} | Sessões ativas: {}", credentialStore.size(), activeSessions.size());
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
