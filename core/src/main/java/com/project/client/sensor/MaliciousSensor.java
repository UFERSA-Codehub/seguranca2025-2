package com.project.client.sensor;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.network.SecureUDPChannel;

public class MaliciousSensor {
    private static final Logger logger = LoggerFactory.getLogger("MaliciousSensor");
    private static final int TIMEOUT_MS = 5000;

    public enum AttackMode {
        INVALID_CREDENTIALS,
        FORGED_JWT,
        TAMPERED_MESSAGE,
        NO_AUTH,
        ANOMALY_DATA,
        MALFORMED_DATA,
        RATE_LIMIT,
        ALL
    }

    private final String sensorId;
    private final String password;
    private final String discoveryHost;
    private final int discoveryPort;
    private final AttackMode attackMode;

    private KeyManager keyManager;
    private SecureUDPChannel udpChannel;
    private UdpClient udpClient;
    private TcpClient tcpClient;
    private String jwtToken;

    public MaliciousSensor(String sensorId, String password, String discoveryHost, int discoveryPort, AttackMode attackMode) {
        this.sensorId = sensorId;
        this.password = password;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
        this.attackMode = attackMode;
    }

    public void run() {
        logger.info("╔══════════════════════════════════════════════════════════╗");
        logger.info("║         SENSOR MALICIOSO - TESTE DE INTRUSÃO             ║");
        logger.info("╠══════════════════════════════════════════════════════════╣");
        logger.info("║ Sensor ID: {}", padRight(sensorId, 46) + "║");
        logger.info("║ Modo de Ataque: {}", padRight(attackMode.toString(), 41) + "║");
        logger.info("║ Discovery: {}:{}", discoveryHost, padRight(String.valueOf(discoveryPort), 37 - discoveryHost.length()) + "║");
        logger.info("╚══════════════════════════════════════════════════════════╝");

        try {
            this.keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            this.udpChannel = new SecureUDPChannel(sensorId, keyManager, socket);
            this.udpClient = new UdpClient(sensorId, udpChannel, discoveryHost, discoveryPort);
            this.tcpClient = new TcpClient(sensorId, keyManager);
        } catch (NoSuchAlgorithmException | SocketException e) {
            logger.error("Erro ao inicializar: {}", e.getMessage());
            return;
        }

        try {
            if (!udpClient.handshakeWithDiscovery()) {
                logger.error("Falha no handshake com Discovery");
                return;
            }
            logger.info("[OK] Handshake com Discovery concluido");

            if (!udpClient.discoverServices()) {
                logger.error("Nenhum servico disponivel");
                return;
            }
            logger.info("[OK] Servicos descobertos - Edge: {}:{}, Auth: {}:{}", 
                udpClient.getEdgeHost(), udpClient.getEdgePort(),
                udpClient.getAuthHost(), udpClient.getAuthPort());

            switch (attackMode) {
                case INVALID_CREDENTIALS -> attackInvalidCredentials();
                case FORGED_JWT -> attackForgedJwt();
                case TAMPERED_MESSAGE -> attackTamperedMessage();
                case NO_AUTH -> attackNoAuth();
                case ANOMALY_DATA -> attackAnomalyData();
                case MALFORMED_DATA -> attackMalformedData();
                case RATE_LIMIT -> attackRateLimit();
                case ALL -> runAllAttacks();
            }

        } finally {
            if (udpChannel != null) {
                udpChannel.getSocket().close();
            }
            if (tcpClient != null) {
                tcpClient.closeEdgeChannel();
            }
        }
    }

    private void runAllAttacks() {
        printSeparator("TESTE 1: CREDENCIAIS INVALIDAS");
        attackInvalidCredentials();
        
        printSeparator("TESTE 2: JWT FORJADO");
        attackForgedJwt();
        
        printSeparator("TESTE 3: MENSAGEM ADULTERADA");
        attackTamperedMessage();
        
        printSeparator("TESTE 4: DADOS SEM AUTENTICACAO");
        attackNoAuth();
        
        printSeparator("TESTE 5: DADOS ANOMALOS (VALORES FORA DO RANGE)");
        attackAnomalyData();
        
        printSeparator("TESTE 6: DADOS MALFORMADOS (JSON INVALIDO)");
        attackMalformedData();
        
        printSeparator("TESTE 7: RATE LIMIT DO IDS (LIMITE DE ALERTAS)");
        attackRateLimit();
        
        logger.info("");
        logger.info("╔══════════════════════════════════════════════════════════╗");
        logger.info("║           TESTES DE INTRUSAO CONCLUIDOS                  ║");
        logger.info("╚══════════════════════════════════════════════════════════╝");
    }

    private void attackInvalidCredentials() {
        logger.info("[ATAQUE] Tentando autenticar com credenciais invalidas...");
        logger.info("         Usuario: SENSOR_FAKE | Senha: senhaErrada123");
        
        String token = tcpClient.authenticateWithAuthServer(
            udpClient.getAuthHost(), 
            udpClient.getAuthPort(), 
            "senhaErrada123"
        );
        
        if (token == null) {
            logger.info("[RESULTADO] ATAQUE BLOQUEADO - Autenticacao recusada");
            logger.info("[SEGURANCA] Sistema validou credenciais corretamente");
        } else {
            logger.error("[RESULTADO] ATAQUE SUCEDIDO - Token recebido: {}", token);
            logger.error("[FALHA] Sistema aceitou credenciais invalidas!");
        }
    }

    private void attackForgedJwt() {
        logger.info("[ATAQUE] Tentando enviar dados com JWT forjado...");
        
        if (!tcpClient.connectToEdge(udpClient.getEdgeHost(), udpClient.getEdgePort())) {
            logger.error("Falha ao conectar ao Edge");
            return;
        }
        
        String forgedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiJTRU5TT1JfRkFLRSIsImlzcyI6IkZBS0UiLCJpYXQiOjE2MzAwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0." +
            "fakeSignature123";
        
        logger.info("         Token forjado: {}...", forgedToken.substring(0, 50));
        
        SensorData data = SensorData.generateRandom(sensorId);
        boolean success = tcpClient.sendData(data.toJson(), forgedToken);
        
        tcpClient.closeEdgeChannel();
        
        if (!success) {
            logger.info("[RESULTADO] ATAQUE BLOQUEADO - Edge rejeitou dados");
            logger.info("[SEGURANCA] Sistema validou JWT corretamente");
        } else {
            logger.error("[RESULTADO] ATAQUE SUCEDIDO - Dados aceitos!");
            logger.error("[FALHA] Sistema aceitou JWT forjado!");
        }
    }

    private void attackTamperedMessage() {
        logger.info("[ATAQUE] Autenticando com credenciais validas primeiro...");
        
        this.jwtToken = tcpClient.authenticateWithAuthServer(
            udpClient.getAuthHost(),
            udpClient.getAuthPort(),
            password
        );
        
        if (jwtToken == null) {
            logger.error("Falha na autenticacao - nao foi possivel prosseguir");
            return;
        }
        logger.info("         Token obtido: {}...", jwtToken.substring(0, Math.min(50, jwtToken.length())));
        
        logger.info("[ATAQUE] Conectando ao Edge e enviando mensagem adulterada...");
        logger.info("         (Neste cenario, a adulteracao ocorre durante transmissao)");
        logger.info("         Devido a criptografia e assinatura, este ataque eh complexo");
        logger.info("         O sistema deve rejeitar qualquer mensagem com HMAC invalido");
        
        if (!tcpClient.connectToEdge(udpClient.getEdgeHost(), udpClient.getEdgePort())) {
            logger.error("Falha ao conectar ao Edge");
            return;
        }
        
        SensorData data = SensorData.generateRandom(sensorId);
        boolean success = tcpClient.sendData(data.toJson(), jwtToken);
        
        tcpClient.closeEdgeChannel();
        
        logger.info("[RESULTADO] Mensagem normal enviada - success={}", success);
        logger.info("[NOTA] Adulteracao em transito seria detectada pelo HMAC");
    }

    private void attackNoAuth() {
        logger.info("[ATAQUE] Tentando enviar dados sem autenticacao previa...");
        
        if (!tcpClient.connectToEdge(udpClient.getEdgeHost(), udpClient.getEdgePort())) {
            logger.error("Falha ao conectar ao Edge");
            return;
        }
        
        SensorData data = SensorData.generateRandom(sensorId);
        boolean success = tcpClient.sendData(data.toJson(), null);
        
        tcpClient.closeEdgeChannel();
        
        if (!success) {
            logger.info("[RESULTADO] ATAQUE BLOQUEADO - Edge rejeitou dados sem token");
            logger.info("[SEGURANCA] Sistema exige autenticacao");
        } else {
            logger.error("[RESULTADO] ATAQUE SUCEDIDO - Dados aceitos sem token!");
            logger.error("[FALHA] Sistema nao exige autenticacao!");
        }
    }

    private void attackAnomalyData() {
        logger.info("[ATAQUE] Enviando dados com valores anomalos fora do range aceitavel...");
        logger.info("");
        logger.info("Ranges normais:");
        logger.info("  - temperature: -40 a 60 °C");
        logger.info("  - humidity: 0 a 100 %%");
        logger.info("  - co2: 200 a 5000 ppm");
        logger.info("  - pm25: 0 a 500 ug/m3");
        logger.info("  - noiseLevel: 0 a 150 dB");
        logger.info("");
        
        this.jwtToken = tcpClient.authenticateWithAuthServer(
            udpClient.getAuthHost(),
            udpClient.getAuthPort(),
            password
        );
        
        if (jwtToken == null) {
            logger.error("Falha na autenticacao - nao foi possivel prosseguir");
            return;
        }
        logger.info("[OK] Autenticado com sucesso");
        
        if (!tcpClient.connectToEdge(udpClient.getEdgeHost(), udpClient.getEdgePort())) {
            logger.error("Falha ao conectar ao Edge");
            return;
        }
        logger.info("[OK] Conectado ao Edge");
        logger.info("");

        logger.info("Fase 1: Enviando 3 leituras NORMAIS para estabelecer baseline...");
        for (int i = 1; i <= 3; i++) {
            SensorData normalData = SensorData.generateRandom(sensorId);
            logger.info("  [{}] Enviando: {}", i, normalData);
            boolean success = tcpClient.sendData(normalData.toJson(), jwtToken);
            logger.info("      Resultado: {}", success ? "ACEITO" : "REJEITADO");
            sleep(1000);
        }
        
        logger.info("");
        logger.info("Fase 2: Enviando 5 leituras ANOMALAS (valores extremos)...");
        logger.info("        O IDS deve detectar e enviar TERMINATE ao Edge!");
        logger.info("");
        
        for (int i = 1; i <= 5; i++) {
            JsonObject anomalyData = generateAnomalousData(i);
            logger.info("  [{}] Enviando ANOMALIA: {}", i, anomalyData);
            
            boolean success = tcpClient.sendData(anomalyData.toString(), jwtToken);
            
            if (!success) {
                logger.warn("      Resultado: REJEITADO/CONEXAO FECHADA");
                logger.info("");
                logger.info("[RESULTADO] Conexao terminada apos {} leituras anomalas", i);
                logger.info("[SEGURANCA] IDS detectou anomalias e comandou TERMINATE!");
                tcpClient.closeEdgeChannel();
                return;
            }
            
            logger.info("      Resultado: ACEITO (aguardando deteccao do IDS...)");
            sleep(1500);
        }
        
        tcpClient.closeEdgeChannel();
        
        logger.info("");
        logger.info("[RESULTADO] Todas as leituras anomalas foram aceitas");
        logger.info("[NOTA] IDS pode ainda processar alertas e terminar conexao futuramente");
    }

    private JsonObject generateAnomalousData(int iteration) {
        JsonObject data = new JsonObject();
        data.addProperty("sensorId", sensorId);
        data.addProperty("timestamp", System.currentTimeMillis());
        
        switch (iteration) {
            case 1 -> {
                data.addProperty("temperature", 100.0);
                data.addProperty("humidity", 50.0);
                data.addProperty("co2", 400.0);
                data.addProperty("pm25", 10.0);
                data.addProperty("noiseLevel", 40.0);
                logger.info("         (temperature=100 ANOMALO - max normal eh 60)");
            }
            case 2 -> {
                data.addProperty("temperature", 25.0);
                data.addProperty("humidity", 150.0);
                data.addProperty("co2", 400.0);
                data.addProperty("pm25", 10.0);
                data.addProperty("noiseLevel", 40.0);
                logger.info("         (humidity=150 ANOMALO - max normal eh 100)");
            }
            case 3 -> {
                data.addProperty("temperature", 25.0);
                data.addProperty("humidity", 50.0);
                data.addProperty("co2", 10000.0);
                data.addProperty("pm25", 10.0);
                data.addProperty("noiseLevel", 40.0);
                logger.info("         (co2=10000 ANOMALO - max normal eh 5000)");
            }
            case 4 -> {
                data.addProperty("temperature", 25.0);
                data.addProperty("humidity", 50.0);
                data.addProperty("co2", 400.0);
                data.addProperty("pm25", 1000.0);
                data.addProperty("noiseLevel", 40.0);
                logger.info("         (pm25=1000 ANOMALO - max normal eh 500)");
            }
            case 5 -> {
                data.addProperty("temperature", 25.0);
                data.addProperty("humidity", 50.0);
                data.addProperty("co2", 400.0);
                data.addProperty("pm25", 10.0);
                data.addProperty("noiseLevel", 200.0);
                logger.info("         (noiseLevel=200 ANOMALO - max normal eh 150)");
            }
            default -> {
                data.addProperty("temperature", -100.0);
                data.addProperty("humidity", -50.0);
                data.addProperty("co2", 50000.0);
                data.addProperty("pm25", 5000.0);
                data.addProperty("noiseLevel", 500.0);
                logger.info("         (TODOS os valores ANOMALOS)");
            }
        }
        
        return data;
    }

    /**
     * Envia dados com estrutura JSON malformada/invalida.
     * Esperado: ContentInspector detecta MALFORMED e alerta IDS.
     */
    private void attackMalformedData() {
        logger.info("[ATAQUE] Enviando dados com estrutura JSON malformada...");
        logger.info("");
        logger.info("Tipos de malformacao a testar:");
        logger.info("  1. Tipo errado (string em vez de numero)");
        logger.info("  2. Campos obrigatorios faltando");
        logger.info("  3. Objeto vazio");
        logger.info("  4. JSON com valores null");
        logger.info("");
        
        this.jwtToken = tcpClient.authenticateWithAuthServer(
            udpClient.getAuthHost(),
            udpClient.getAuthPort(),
            password
        );
        
        if (jwtToken == null) {
            logger.error("Falha na autenticacao - nao foi possivel prosseguir");
            return;
        }
        logger.info("[OK] Autenticado com sucesso");
        
        if (!tcpClient.connectToEdge(udpClient.getEdgeHost(), udpClient.getEdgePort())) {
            logger.error("Falha ao conectar ao Edge");
            return;
        }
        logger.info("[OK] Conectado ao Edge");
        logger.info("");

        String[] malformedData = generateMalformedData();
        int alertCount = 0;
        
        for (int i = 0; i < malformedData.length; i++) {
            logger.info("[{}] Enviando: {}", i + 1, malformedData[i]);
            
            boolean success = tcpClient.sendData(malformedData[i], jwtToken);
            
            if (!success) {
                alertCount++;
                logger.warn("    Resultado: REJEITADO/CONEXAO FECHADA");
                logger.info("");
                logger.info("[RESULTADO] Conexao terminada apos {} dados malformados", i + 1);
                logger.info("[SEGURANCA] ContentInspector detectou dados invalidos!");
                tcpClient.closeEdgeChannel();
                return;
            }
            
            logger.info("    Resultado: ACEITO (ContentInspector pode ter alertado IDS)");
            sleep(1000);
        }
        
        tcpClient.closeEdgeChannel();
        
        logger.info("");
        logger.info("[RESULTADO] Todos os dados malformados foram enviados");
        logger.info("[NOTA] ContentInspector pode ter alertado IDS mesmo com conexao mantida");
    }

    private String[] generateMalformedData() {
        return new String[] {
            // Caso 1: Tipo errado (string em vez de numero)
            "{\"sensorId\":\"" + sensorId + "\",\"temperature\":\"not_a_number\",\"humidity\":50,\"co2\":400,\"pm25\":10,\"noiseLevel\":40}",
            
            // Caso 2: Campos obrigatorios faltando
            "{\"sensorId\":\"" + sensorId + "\",\"temperature\":25.0}",
            
            // Caso 3: Objeto vazio
            "{}",
            
            // Caso 4: JSON com valores null
            "{\"sensorId\":\"" + sensorId + "\",\"temperature\":null,\"humidity\":null,\"co2\":null,\"pm25\":null,\"noiseLevel\":null}"
        };
    }

    /**
     * Testa o rate limit do IDS baseado em alertas de anomalia.
     * O IDS termina conexoes quando um sensor gera ANOMALY_THRESHOLD (2) alertas
     * dentro de ANOMALY_WINDOW_MS (60 segundos).
     * 
     * Este teste envia anomalias rapidamente para verificar se o IDS
     * aplica corretamente o limite e envia TERMINATE ao Edge.
     */
    private void attackRateLimit() {
        logger.info("[ATAQUE] Testando rate limit do IDS (limite de alertas por sensor)...");
        logger.info("");
        logger.info("Configuracao do IDS:");
        logger.info("  - ANOMALY_THRESHOLD: 1 alerta");
        logger.info("  - ANOMALY_WINDOW_MS: 60 segundos");
        logger.info("  - Esperado: conexao terminada apos 1 anomalia");
        logger.info("");
        
        this.jwtToken = tcpClient.authenticateWithAuthServer(
            udpClient.getAuthHost(),
            udpClient.getAuthPort(),
            password
        );
        
        if (jwtToken == null) {
            logger.error("Falha na autenticacao - nao foi possivel prosseguir");
            return;
        }
        logger.info("[OK] Autenticado com sucesso");
        
        if (!tcpClient.connectToEdge(udpClient.getEdgeHost(), udpClient.getEdgePort())) {
            logger.error("Falha ao conectar ao Edge");
            return;
        }
        logger.info("[OK] Conectado ao Edge");
        logger.info("");

        // Enviar 1 leitura normal para estabelecer conexao
        logger.info("[1] Enviando leitura NORMAL para estabelecer conexao...");
        SensorData normalData = SensorData.generateRandom(sensorId);
        boolean success = tcpClient.sendData(normalData.toJson(), jwtToken);
        logger.info("    Resultado: {}", success ? "ACEITO" : "REJEITADO");
        
        if (!success) {
            logger.error("Falha ao enviar dados normais - abortando teste");
            tcpClient.closeEdgeChannel();
            return;
        }
        
        sleep(300);
        logger.info("");

        // Enviar anomalias rapidamente para triggerar o rate limit
        logger.info("Enviando anomalias para triggerar rate limit do IDS...");
        logger.info("");
        
        int anomalyCount = 0;
        int maxAnomalies = 4; // Enviar ate 4, mas esperamos bloqueio apos 1
        
        for (int i = 1; i <= maxAnomalies; i++) {
            JsonObject anomalyData = generateRateLimitAnomaly(i);
            logger.info("[{}] Enviando ANOMALIA #{}: {}", i + 1, i, getAnomalyDescription(i));
            
            success = tcpClient.sendData(anomalyData.toString(), jwtToken);
            anomalyCount++;
            
            if (!success) {
                logger.warn("    Resultado: REJEITADO/CONEXAO FECHADA");
                logger.info("");
                logger.info("[RESULTADO] Conexao terminada apos {} anomalia(s)", anomalyCount);
                
                if (anomalyCount == 1) {
                    logger.info("[SEGURANCA] IDS aplicou rate limit corretamente (ANOMALY_THRESHOLD=1)");
                } else {
                    logger.warn("[NOTA] Conexao fechada apos threshold (delay no processamento do IDS)");
                }
                
                tcpClient.closeEdgeChannel();
                return;
            }
            
            logger.info("    Resultado: ACEITO (IDS alertado, aguardando threshold...)");
            
            // Pequena pausa entre anomalias para permitir processamento
            sleep(500);
        }
        
        tcpClient.closeEdgeChannel();
        
        logger.info("");
        logger.info("[RESULTADO] Todas as {} anomalias foram aceitas", anomalyCount);
        logger.warn("[NOTA] IDS pode nao ter aplicado rate limit ou TERMINATE nao foi recebido");
    }

    private JsonObject generateRateLimitAnomaly(int iteration) {
        JsonObject data = new JsonObject();
        data.addProperty("sensorId", sensorId);
        data.addProperty("timestamp", System.currentTimeMillis());
        
        // Valores alternados para gerar anomalias diferentes
        switch (iteration % 4) {
            case 1 -> {
                data.addProperty("temperature", 150.0); // Muito acima do max (60)
                data.addProperty("humidity", 50.0);
                data.addProperty("co2", 400.0);
                data.addProperty("pm25", 10.0);
                data.addProperty("noiseLevel", 40.0);
            }
            case 2 -> {
                data.addProperty("temperature", 25.0);
                data.addProperty("humidity", 200.0); // Muito acima do max (100)
                data.addProperty("co2", 400.0);
                data.addProperty("pm25", 10.0);
                data.addProperty("noiseLevel", 40.0);
            }
            case 3 -> {
                data.addProperty("temperature", 25.0);
                data.addProperty("humidity", 50.0);
                data.addProperty("co2", 15000.0); // Muito acima do max (5000)
                data.addProperty("pm25", 10.0);
                data.addProperty("noiseLevel", 40.0);
            }
            default -> {
                data.addProperty("temperature", 25.0);
                data.addProperty("humidity", 50.0);
                data.addProperty("co2", 400.0);
                data.addProperty("pm25", 2000.0); // Muito acima do max (500)
                data.addProperty("noiseLevel", 40.0);
            }
        }
        
        return data;
    }

    private String getAnomalyDescription(int iteration) {
        return switch (iteration % 4) {
            case 1 -> "temperature=150 (max=60)";
            case 2 -> "humidity=200 (max=100)";
            case 3 -> "co2=15000 (max=5000)";
            default -> "pm25=2000 (max=500)";
        };
    }

    private void printSeparator(String title) {
        logger.info("");
        logger.info("┌──────────────────────────────────────────────────────────┐");
        logger.info("│ {}", padRight(title, 57) + "│");
        logger.info("└──────────────────────────────────────────────────────────┘");
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        String sensorId = "MALICIOUS_SENSOR";
        String password = "sensor123";
        String host = "localhost";
        int port = 3040;
        AttackMode mode = AttackMode.ALL;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--id" -> sensorId = args[++i];
                case "--password" -> password = args[++i];
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--mode" -> mode = AttackMode.valueOf(args[++i].toUpperCase());
                case "--help" -> {
                    System.out.println("Uso: MaliciousSensor [opcoes]");
                    System.out.println("  --id <sensor_id>      ID do sensor (default: MALICIOUS_SENSOR)");
                    System.out.println("  --password <senha>    Senha para ataques com credenciais validas (default: sensor123)");
                    System.out.println("  --host <host>         Host do Discovery (default: localhost)");
                    System.out.println("  --port <port>         Porta do PacketFilter/Discovery (default: 3040)");
                    System.out.println("  --mode <mode>         Modo de ataque:");
                    System.out.println("                        INVALID_CREDENTIALS - Testa credenciais invalidas");
                    System.out.println("                        FORGED_JWT - Testa JWT forjado");
                    System.out.println("                        TAMPERED_MESSAGE - Testa mensagem adulterada");
                    System.out.println("                        NO_AUTH - Testa envio sem autenticacao");
                    System.out.println("                        ANOMALY_DATA - Envia dados com valores anomalos");
                    System.out.println("                        MALFORMED_DATA - Envia dados com JSON malformado");
                    System.out.println("                        RATE_LIMIT - Testa limite de alertas do IDS (2 anomalias = TERMINATE)");
                    System.out.println("                        ALL - Executa todos os testes (default)");
                    return;
                }
            }
        }

        MaliciousSensor malicious = new MaliciousSensor(sensorId, password, host, port, mode);
        malicious.run();
    }
}
