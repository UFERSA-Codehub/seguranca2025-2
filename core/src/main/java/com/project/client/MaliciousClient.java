package com.project.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cliente malicioso para testar as regras do PacketFilter.
 * Testa: port scan, rate limit, portas nao mapeadas, blacklist.
 */
public class MaliciousClient {
    private static final Logger logger = LoggerFactory.getLogger("MaliciousClient");

    private static final int CONNECTION_TIMEOUT_MS = 2000;
    private static final int[] VALID_PORTS = {3000, 3010, 3020, 3030};
    private static final int HONEYPOT_PORT = 3005;

    public enum AttackMode {
        PORT_SCAN,
        RATE_LIMIT,
        HONEYPOT,
        BLACKLIST_TEST,
        ALL
    }

    private final String targetHost;
    private final AttackMode attackMode;

    public MaliciousClient(String targetHost, AttackMode attackMode) {
        this.targetHost = targetHost;
        this.attackMode = attackMode;
    }

    public void run() {
        printHeader();

        switch (attackMode) {
            case PORT_SCAN -> attackPortScan();
            case RATE_LIMIT -> attackRateLimit();
            case HONEYPOT -> attackHoneypot();
            case BLACKLIST_TEST -> attackBlacklistTest();
            case ALL -> runAllAttacks();
        }

        printFooter();
    }

    private void runAllAttacks() {
        printSeparator("TESTE 1: HONEYPOT (DEFAULT DENY)");
        attackHoneypot();

        printSeparator("TESTE 2: PORT SCAN");
        attackPortScan();

        printSeparator("TESTE 3: BLACKLIST (apos port scan)");
        attackBlacklistTest();

        // Rate limit nao pode ser testado apos blacklist (IP ja bloqueado)
        logger.info("");
        logger.info("[NOTA] Rate limit nao testado - IP ja esta na blacklist");
        logger.info("[NOTA] Para testar rate limit, reinicie o sistema e use --mode RATE_LIMIT");
    }

    /**
     * Tenta conectar a porta honeypot (3005).
     * PacketFilter escuta nesta porta, mas NAO tem regra de filtro para ela.
     * Esperado: conexao aceita pelo listener, bloqueada pela politica default deny.
     */
    private void attackHoneypot() {
        logger.info("[ATAQUE] Tentando conectar a porta honeypot: {}", HONEYPOT_PORT);
        logger.info("         PacketFilter escuta nesta porta (listener ativo)");
        logger.info("         Mas NAO existe regra de filtro - default deny deve bloquear");
        logger.info("");

        boolean connected = tryConnect(HONEYPOT_PORT);

        if (!connected) {
            logger.info("[RESULTADO] ATAQUE BLOQUEADO - Conexao fechada pelo firewall");
            logger.info("[SEGURANCA] PacketFilter aplicou politica default deny");
        } else {
            logger.error("[RESULTADO] ATAQUE SUCEDIDO - Conexao aceita!");
            logger.error("[FALHA] PacketFilter nao aplicou default deny!");
        }
    }

    /**
     * Conecta a 3+ portas diferentes em menos de 5 segundos.
     * Esperado: 3a conexao bloqueada, IP adicionado a blacklist.
     */
    private void attackPortScan() {
        logger.info("[ATAQUE] Executando port scan - conectando a multiplas portas");
        logger.info("         Threshold: 3 portas em 5 segundos");
        logger.info("         Esperado: 3a conexao bloqueada + IP na blacklist");
        logger.info("");

        int blockedAt = -1;
        for (int i = 0; i < VALID_PORTS.length; i++) {
            int port = VALID_PORTS[i];
            boolean connected = tryConnect(port);

            String status = connected ? "ACEITO" : "BLOQUEADO";
            logger.info("[{}] Porta {} - {}", i + 1, port, status);

            if (!connected && blockedAt == -1) {
                blockedAt = i + 1;
            }

            // Pequena pausa para nao misturar com rate limit
            sleep(100);
        }

        logger.info("");
        if (blockedAt == 3) {
            logger.info("[RESULTADO] ATAQUE DETECTADO na conexao {}", blockedAt);
            logger.info("[SEGURANCA] PacketFilter detectou port scan corretamente");
        } else if (blockedAt > 0) {
            logger.info("[RESULTADO] ATAQUE DETECTADO na conexao {} (esperado: 3)", blockedAt);
            logger.info("[NOTA] Comportamento diferente do esperado");
        } else {
            logger.error("[RESULTADO] ATAQUE NAO DETECTADO - todas conexoes aceitas!");
            logger.error("[FALHA] PacketFilter nao detectou port scan!");
        }
    }

    /**
     * Conecta mais de 5 vezes por segundo na mesma porta.
     * Esperado: conexoes apos a 5a bloqueadas.
     */
    private void attackRateLimit() {
        logger.info("[ATAQUE] Executando flood de conexoes (rate limit test)");
        logger.info("         Limite: 5 conexoes/segundo por IP:porta");
        logger.info("         Enviando 10 conexoes rapidas na porta {}", VALID_PORTS[0]);
        logger.info("");

        int port = VALID_PORTS[0];
        int accepted = 0;
        int blocked = 0;
        int firstBlocked = -1;

        for (int i = 1; i <= 10; i++) {
            boolean connected = tryConnect(port);

            if (connected) {
                accepted++;
                logger.info("[{}] Conexao ACEITA", i);
            } else {
                blocked++;
                if (firstBlocked == -1) {
                    firstBlocked = i;
                }
                logger.info("[{}] Conexao BLOQUEADA", i);
            }

            // Sem pausa - queremos exceder o rate limit
        }

        logger.info("");
        logger.info("Resultado: {} aceitas, {} bloqueadas", accepted, blocked);

        if (blocked > 0 && firstBlocked > 5) {
            logger.info("[RESULTADO] RATE LIMIT ATIVO - bloqueio apos {} conexoes", firstBlocked);
            logger.info("[SEGURANCA] PacketFilter aplicou rate limit corretamente");
        } else if (blocked > 0) {
            logger.info("[RESULTADO] BLOQUEIO detectado na conexao {}", firstBlocked);
            logger.info("[NOTA] Pode ser port scan residual ou rate limit");
        } else {
            logger.warn("[RESULTADO] NENHUM BLOQUEIO - rate limit pode nao ter sido ativado");
            logger.warn("[NOTA] Conexoes podem ter sido muito lentas para exceder o limite");
        }
    }

    /**
     * Apos entrar na blacklist (via port scan), tenta conectar novamente.
     * Esperado: todas as conexoes bloqueadas.
     */
    private void attackBlacklistTest() {
        logger.info("[ATAQUE] Testando se IP esta na blacklist");
        logger.info("         Apos port scan, IP deve ter sido adicionado a blacklist");
        logger.info("         Tentando conectar em porta valida...");
        logger.info("");

        int port = VALID_PORTS[0];
        boolean connected = tryConnect(port);

        if (!connected) {
            logger.info("[RESULTADO] CONEXAO BLOQUEADA");
            logger.info("[SEGURANCA] IP permanece na blacklist corretamente");
        } else {
            logger.warn("[RESULTADO] CONEXAO ACEITA");
            logger.warn("[NOTA] IP pode nao estar na blacklist (port scan nao executado?)");
        }
    }

    /**
     * Tenta estabelecer conexao TCP com o PacketFilter e verifica se foi mantida.
     * O PacketFilter aceita a conexao primeiro, depois verifica regras e fecha se bloqueada.
     * Retorna true se a conexao foi aceita E mantida, false se recusada/fechada.
     */
    private boolean tryConnect(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, port), CONNECTION_TIMEOUT_MS);
            
            // Conexao TCP estabelecida, mas PacketFilter pode fechar imediatamente
            // Tentar ler para detectar se o servidor fechou a conexao
            socket.setSoTimeout(500); // Timeout curto para leitura
            
            try {
                int result = socket.getInputStream().read();
                // Se recebemos dados ou -1 (EOF = servidor fechou), verificar
                if (result == -1) {
                    // Servidor fechou a conexao = bloqueado pelo PacketFilter
                    return false;
                }
                // Recebeu dados = conexao aceita e ativa
                return true;
            } catch (java.net.SocketTimeoutException e) {
                // Timeout sem dados = conexao ainda aberta, servidor esperando protocolo
                // Isso significa que a conexao foi ACEITA e encaminhada ao ReverseProxy
                return true;
            } catch (IOException e) {
                // Erro de leitura (connection reset, etc) = servidor fechou
                return false;
            }
        } catch (IOException e) {
            // Conexao recusada ou timeout no connect
            return false;
        }
    }

    private void printHeader() {
        logger.info("");
        logger.info("======================================================================");
        logger.info("         CLIENTE MALICIOSO - TESTE DO PACKET FILTER                  ");
        logger.info("======================================================================");
        logger.info(" Modo: {}", attackMode);
        logger.info(" Host: {}", targetHost);
        logger.info(" Portas validas: 3000, 3010, 3020, 3030");
        logger.info(" Porta honeypot: {} (listener sem regra de filtro)", HONEYPOT_PORT);
        logger.info("======================================================================");
        logger.info("");
    }

    private void printFooter() {
        logger.info("");
        logger.info("======================================================================");
        logger.info("                    TESTES CONCLUIDOS                                ");
        logger.info("======================================================================");
    }

    private void printSeparator(String title) {
        logger.info("");
        logger.info("----------------------------------------------------------------------");
        logger.info(" {}", title);
        logger.info("----------------------------------------------------------------------");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        AttackMode mode = AttackMode.ALL;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--mode" -> mode = AttackMode.valueOf(args[++i].toUpperCase());
                case "--help" -> {
                    printUsage();
                    return;
                }
            }
        }

        MaliciousClient client = new MaliciousClient(host, mode);
        client.run();
    }

    private static void printUsage() {
        System.out.println("Uso: MaliciousClient [opcoes]");
        System.out.println("  --host <host>    Host do PacketFilter (default: localhost)");
        System.out.println("  --mode <mode>    Modo de ataque:");
        System.out.println("                   PORT_SCAN      - Conecta a 3+ portas em 5s (detecta port scan)");
        System.out.println("                   RATE_LIMIT     - Conecta >5x/s na mesma porta");
        System.out.println("                   HONEYPOT       - Conecta em porta honeypot (default deny)");
        System.out.println("                   BLACKLIST_TEST - Testa se IP esta bloqueado apos port scan");
        System.out.println("                   ALL            - Executa todos os testes (default)");
        System.out.println("  --help           Mostra esta ajuda");
    }
}
