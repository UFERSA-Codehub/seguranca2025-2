package com.project.server.discovery;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.crypto.KeyManager;
import com.project.network.SecureUDPChannel;
import com.project.network.SecureUDPChannel.ReceivedPacket;
import com.project.server.IServer;

public class ServerDiscovery implements IServer {
    private static final Logger logger = LoggerFactory.getLogger("Discovery");

    private final String name;
    private final int port;
    private volatile boolean running;
    
    private SecureUDPChannel channel;
    private ServiceRegistry registry;
    private UdpHandler udpHandler;

    public ServerDiscovery(int port) {
        this.name = "DISCOVERY";
        this.port = port;
        this.running = false;
    }

    @Override
    public void start() {
        logger.info("[Discovery] Iniciando na porta {}...", port);
        
        try {
            KeyManager keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket(port);
            this.channel = new SecureUDPChannel(name, keyManager, socket);
            this.running = true;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erro ao inicializar KeyManager: {}", e.getMessage());
            return;
        } catch (SocketException e) {
            logger.error("Erro ao abrir socket na porta {}: {}", port, e.getMessage());
            return;
        }

        // Inicializar registry e handler
        this.registry = new ServiceRegistry(channel);
        this.udpHandler = new UdpHandler(name, channel, registry);

        // Iniciar verificador de timeout de heartbeat
        registry.startTimeoutChecker();

        logger.info("[Discovery] Iniciado na porta {}", port);

        // Loop principal de recebimento de mensagens
        while (running) {
            ReceivedPacket packet = channel.receive();
            if (packet != null) {
                udpHandler.handle(packet.message(), packet.address(), packet.port());
            }
        }
    }

    @Override
    public void stop() {
        logger.info("[Discovery] Parando...");
        this.running = false;

        if (registry != null) {
            registry.stopTimeoutChecker();
        }

        if (channel != null && channel.getSocket() != null) {
            channel.getSocket().close();
        }
        
        logger.info("[Discovery] Parado");
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
        logger.info("=== Status Discovery ===");
        logger.info("Porta: {} | Status: {}", port, running ? "Ativo" : "Inativo");
        if (registry != null) {
            logger.info("Serviços: {} EDGEs, {} DATACENTERs, {} AUTHs", 
                       registry.getEdgeCount(), registry.getDatacenterCount(), registry.getAuthServerCount());
        }
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 4000;
        ServerDiscovery server = new ServerDiscovery(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
