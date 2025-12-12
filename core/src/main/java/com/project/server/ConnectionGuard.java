package com.project.server;

import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário para controle de acesso baseado em IP.
 * Servidores internos (Edge, Datacenter, Auth) devem aceitar conexões apenas
 * de IPs autorizados (ReverseProxy, localhost, IDS).
 * 
 * Isso implementa o princípio de defesa em profundidade: mesmo que um atacante
 * consiga descobrir as portas internas, não conseguirá conectar diretamente.
 */
public class ConnectionGuard {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionGuard.class);

    // IPs sempre permitidos (localhost em diferentes representações)
    private static final Set<String> LOCALHOST_IPS = Set.of(
        "127.0.0.1",
        "localhost",
        "0:0:0:0:0:0:0:1",  // IPv6 localhost
        "::1"               // IPv6 localhost curto
    );

    private final String serverName;
    private final Set<String> allowedIps;

    /**
     * Cria um ConnectionGuard que permite apenas conexões de IPs específicos.
     * Localhost é sempre permitido automaticamente.
     * 
     * @param serverName Nome do servidor (para logs)
     * @param additionalAllowedIps IPs adicionais permitidos (ex: IP do ReverseProxy)
     */
    public ConnectionGuard(String serverName, String... additionalAllowedIps) {
        this.serverName = serverName;
        this.allowedIps = ConcurrentHashMap.newKeySet();
        
        // Sempre permitir localhost
        this.allowedIps.addAll(LOCALHOST_IPS);
        
        // Adicionar IPs extras
        for (String ip : additionalAllowedIps) {
            if (ip != null && !ip.isBlank()) {
                this.allowedIps.add(normalizeIp(ip));
            }
        }
        
        logger.info("[{}] ConnectionGuard inicializado - IPs permitidos: {}", serverName, allowedIps);
    }

    /**
     * Verifica se uma conexão deve ser aceita baseado no IP de origem.
     * 
     * @param socket Socket da conexão
     * @return true se a conexão é permitida, false caso contrário
     */
    public boolean isConnectionAllowed(Socket socket) {
        String remoteIp = extractIp(socket);
        boolean allowed = isIpAllowed(remoteIp);
        
        if (!allowed) {
            logger.warn("[{}] Conexão REJEITADA de IP não autorizado: {}", serverName, remoteIp);
        }
        
        return allowed;
    }

    /**
     * Verifica se um IP está na whitelist.
     */
    public boolean isIpAllowed(String ip) {
        String normalized = normalizeIp(ip);
        return allowedIps.contains(normalized);
    }

    /**
     * Adiciona um IP à whitelist em runtime.
     */
    public void allowIp(String ip) {
        String normalized = normalizeIp(ip);
        if (allowedIps.add(normalized)) {
            logger.info("[{}] IP adicionado à whitelist: {}", serverName, normalized);
        }
    }

    /**
     * Remove um IP da whitelist (exceto localhost).
     */
    public void denyIp(String ip) {
        String normalized = normalizeIp(ip);
        if (!LOCALHOST_IPS.contains(normalized) && allowedIps.remove(normalized)) {
            logger.info("[{}] IP removido da whitelist: {}", serverName, normalized);
        }
    }

    /**
     * Extrai o IP de um socket, removendo porta e prefixos.
     */
    public static String extractIp(Socket socket) {
        String address = socket.getInetAddress().getHostAddress();
        return normalizeIp(address);
    }

    /**
     * Normaliza um IP removendo prefixos e convertendo para formato padrão.
     */
    private static String normalizeIp(String ip) {
        if (ip == null) return "";
        
        // Remover prefixo "/" se presente
        if (ip.startsWith("/")) {
            ip = ip.substring(1);
        }
        
        // Remover porta se presente (formato ip:porta)
        int colonIndex = ip.lastIndexOf(':');
        if (colonIndex > 0 && !ip.contains("::")) {
            // IPv4 com porta
            ip = ip.substring(0, colonIndex);
        }
        
        return ip.trim();
    }

    /**
     * Retorna a lista de IPs permitidos (para debug/logging).
     */
    public Set<String> getAllowedIps() {
        return Set.copyOf(allowedIps);
    }
}
