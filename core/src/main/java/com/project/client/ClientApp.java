package com.project.client;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import com.project.crypto.KeyManager;
import com.project.network.SecureUDPChannel;

public class ClientApp {
    //private static final Logger logger = LoggerFactory.getLogger("Client.App");
    private static final int TIMEOUT_MS = 5000;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final String clientId;
    private final String discoveryHost;
    private final int discoveryPort;
    private final Scanner scanner;

    private KeyManager keyManager;
    private SecureUDPChannel udpChannel;
    private UdpClient udpClient;
    private TcpClient tcpClient;
    private String loggedUser;
    private boolean running;

    public ClientApp(String clientId, String discoveryHost, int discoveryPort) {
        this.clientId = clientId;
        this.discoveryHost = discoveryHost;
        this.discoveryPort = discoveryPort;
        this.scanner = new Scanner(System.in);
        this.running = true;
    }

    public void start() {
        printHeader();

        // Passo 1 - Inicializar componentes de rede e crypto
        if (!initialize()) {
            return;
        }

        // Passo 2 - Descobrir serviços via Discovery
        if (!discoverServices()) {
            return;
        }

        // Passo 3 - Loop do menu principal
        mainLoop();

        // Passo 4 - Encerrar
        stop();
    }

    private boolean initialize() {
        try {
            this.keyManager = new KeyManager();
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            this.udpChannel = new SecureUDPChannel(clientId, keyManager, socket);
            this.udpClient = new UdpClient(clientId, udpChannel, discoveryHost, discoveryPort);
            this.tcpClient = new TcpClient(clientId, keyManager);
            return true;
        } catch (NoSuchAlgorithmException e) {
            System.out.println("[ERRO] Falha ao inicializar criptografia: " + e.getMessage());
            return false;
        } catch (SocketException e) {
            System.out.println("[ERRO] Falha ao abrir socket: " + e.getMessage());
            return false;
        }
    }

    private boolean discoverServices() {
        System.out.println("\n[*] Conectando ao sistema...");

        // Handshake com Discovery
        if (!udpClient.handshakeWithDiscovery()) {
            System.out.println("[ERRO] Falha no handshake com Discovery");
            return false;
        }

        // Descobrir AuthServer e Datacenter
        if (!udpClient.discoverServices()) {
            System.out.println("[ERRO] Não foi possível localizar os serviços");
            return false;
        }

        System.out.println("[OK] Serviços descobertos:");
        System.out.println("     AuthServer: " + udpClient.getAuthHost() + ":" + udpClient.getAuthPort());
        System.out.println("     Datacenter: " + udpClient.getDatacenterHost() + ":" + udpClient.getDatacenterPort());
        return true;
    }

    private void mainLoop() {
        while (running) {
            clearScreen();
            printHeader();
            printMenu();
            String choice = prompt("Opção");

            switch (choice) {
                case "1" -> doLogin();
                case "2" -> doQueryReport();
                case "3" -> doLogout();
                case "0" -> running = false;
                default -> System.out.println("[!] Opção inválida");
            }
        }
    }

    // ==================== MENU ACTIONS ====================

    private void doLogin() {
        if (tcpClient.isAuthenticated()) {
            System.out.println("[!] Você já está logado como: " + loggedUser);
            waitEnter();
            return;
        }

        clearScreen();
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│                  LOGIN                   │");
        System.out.println("└──────────────────────────────────────────┘");
        System.out.println();
        String username = prompt("Usuário");
        String password = prompt("Senha");

        System.out.println("\n[*] Autenticando...");
        String token = tcpClient.authenticateWithAuthServer(
            udpClient.getAuthHost(),
            udpClient.getAuthPort(),
            username,
            password
        );

        if (token != null) {
            this.loggedUser = username;
            System.out.println("[OK] Login bem-sucedido!");

            // Conectar ao Datacenter após login bem-sucedido
            System.out.println("[*] Conectando ao Datacenter...");
            if (tcpClient.connectToDatacenter(udpClient.getDatacenterHost(), udpClient.getDatacenterPort())) {
                System.out.println("[OK] Conectado ao Datacenter");
            } else {
                System.out.println("[ERRO] Falha ao conectar ao Datacenter");
            }
        } else {
            System.out.println("[ERRO] Credenciais inválidas ou erro de conexão");
        }
        waitEnter();
    }

    private void doLogout() {
        if (!tcpClient.isAuthenticated()) {
            System.out.println("[!] Você não está logado");
            waitEnter();
            return;
        }

        tcpClient.closeDatacenterChannel();
        tcpClient.clearToken();
        this.loggedUser = null;
        System.out.println("[OK] Logout realizado");
        waitEnter();
    }

    private void doQueryReport() {
        if (!checkAuth()) return;

        clearScreen();
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│          CONSULTAR RELATÓRIO             │");
        System.out.println("├──────────────────────────────────────────┤");
        System.out.println("│  1. Relatório de Poluição                │");
        System.out.println("│  2. Alerta de Enchente                   │");
        System.out.println("│  3. Mapa de Ruído                        │");
        System.out.println("│  4. Índice UV                            │");
        System.out.println("│  5. Qualidade do Ar                      │");
        System.out.println("│  0. Voltar                               │");
        System.out.println("└──────────────────────────────────────────┘");

        String choice = prompt("Opção");
        String reportType = switch (choice) {
            case "1" -> "pollution";
            case "2" -> "flood";
            case "3" -> "noise";
            case "4" -> "uv";
            case "5" -> "air-quality";
            case "0" -> null;
            default -> null;
        };

        if (reportType == null) {
            return;
        }

        queryReport(reportType);
    }

    private boolean checkAuth() {
        if (!tcpClient.isAuthenticated()) {
            System.out.println("[!] Você precisa fazer login primeiro (opção 1)");
            waitEnter();
            return false;
        }
        if (!tcpClient.isConnectedToDatacenter()) {
            System.out.println("[*] Reconectando ao Datacenter...");
            if (!tcpClient.connectToDatacenter(udpClient.getDatacenterHost(), udpClient.getDatacenterPort())) {
                System.out.println("[ERRO] Falha ao conectar ao Datacenter");
                waitEnter();
                return false;
            }
        }
        return true;
    }

    private void queryReport(String reportType) {
        clearScreen();
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│  " + padRight(getReportTitle(reportType), 39) + " │");
        System.out.println("└──────────────────────────────────────────┘");
        System.out.println();
        System.out.println("[*] Consultando...");

        String reportData = tcpClient.queryReport(reportType, "json");
        if (reportData != null) {
            displayReport(reportType, reportData);
        } else {
            System.out.println("[ERRO] Falha ao obter relatório");
        }
        waitEnter();
    }

    private void displayReport(String reportType, String reportData) {
        JsonObject data = gson.fromJson(reportData, JsonObject.class);

        // Verificar se o JSON foi parseado corretamente
        if (data == null || data.entrySet().isEmpty()) {
            System.out.println("[ERRO] Formato de resposta inválido");
            return;
        }

        // Info básica
        int totalReadings = data.has("totalReadings") ? data.get("totalReadings").getAsInt() : 0;
        long totalSensors = data.has("totalSensors") ? data.get("totalSensors").getAsLong() : 0;

        if (totalReadings == 0) {
            System.out.println("Nenhum dado disponível para o relatório solicitado.");
            return;
        }

        System.out.println();
        System.out.println("Leituras: " + totalReadings + " | Sensores: " + totalSensors);
        System.out.println("────────────────────────────────────────────");

        switch (reportType) {
            case "flood" -> {
                String alertLevel = data.has("alertLevel") ? data.get("alertLevel").getAsString() : "N/A";
                String avgHumidity = data.has("avgHumidity") ? data.get("avgHumidity").getAsString() : "N/A";
                String avgTemp = data.has("avgTemperature") ? data.get("avgTemperature").getAsString() : "N/A";

                System.out.println("Status: " + alertLevel);
                System.out.println("Umidade Média: " + avgHumidity + "%");
                System.out.println("Temperatura Média: " + avgTemp + " C");

                if ("CRÍTICO".equals(alertLevel)) {
                    System.out.println("\n[!] ALERTA DE ENCHENTE DETECTADO");
                } else if ("ATENÇÃO".equals(alertLevel)) {
                    System.out.println("\n[!] ATENÇÃO: Condições de risco");
                }
            }
            case "pollution" -> {
                String co2Status = data.has("co2Status") ? data.get("co2Status").getAsString() : "N/A";
                String avgCo2 = data.has("avgCo2") ? data.get("avgCo2").getAsString() : "N/A";
                String avgNo2 = data.has("avgNo2") ? data.get("avgNo2").getAsString() : "N/A";
                String avgPm25 = data.has("avgPm25") ? data.get("avgPm25").getAsString() : "N/A";

                System.out.println("Status CO2: " + co2Status);
                System.out.println("CO2 Médio: " + avgCo2 + " ppm");
                System.out.println("NO2 Médio: " + avgNo2 + " ug/m3");
                System.out.println("PM2.5 Médio: " + avgPm25 + " ug/m3");
            }
            case "noise" -> {
                String noiseLevel = data.has("noiseLevel") ? data.get("noiseLevel").getAsString() : "N/A";
                String avgNoise = data.has("avgNoise") ? data.get("avgNoise").getAsString() : "N/A";

                System.out.println("Nível de Ruído: " + noiseLevel);
                System.out.println("Média: " + avgNoise + " dB");
            }
            case "uv" -> {
                String uvLevel = data.has("uvLevel") ? data.get("uvLevel").getAsString() : "N/A";
                String avgUv = data.has("avgUv") ? data.get("avgUv").getAsString() : "N/A";

                System.out.println("Nível UV: " + uvLevel);
                System.out.println("Índice Médio: " + avgUv);
            }
            case "air-quality" -> {
                String iqaValue = data.has("iqaValue") ? String.valueOf(data.get("iqaValue").getAsInt()) : "N/A";
                String classification = data.has("iqaClassification") ? data.get("iqaClassification").getAsString() : "N/A";
                String mainPollutant = data.has("mainPollutant") ? data.get("mainPollutant").getAsString() : "N/A";

                System.out.println("IQA: " + iqaValue + " (" + classification + ")");
                System.out.println("Principal Poluente: " + mainPollutant);
            }
        }

        // Recomendação
        if (data.has("recommendation")) {
            String recommendation = data.get("recommendation").getAsString();
            System.out.println("\nRecomendação:");
            System.out.println("  " + recommendation);
        }

        System.out.println();
    }

    // ==================== UI HELPERS ====================

    private void printHeader() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     CLIENTE INTERATIVO - DATACENTER      ║");
        System.out.println("║          Comunicação Cifrada             ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println("  ID: " + clientId);
    }

    private void printMenu() {
        System.out.println();
        System.out.println("┌──────────────────────────────────────────┐");
        if (tcpClient != null && tcpClient.isAuthenticated()) {
            System.out.println("│  Logado como: " + padRight(loggedUser, 26) + " │");
        } else {
            System.out.println("│  Status: Não autenticado                 │");
        }
        System.out.println("├──────────────────────────────────────────┤");
        System.out.println("│  1. Login                                │");
        System.out.println("│  2. Consultar Relatório                  │");
        System.out.println("│  3. Logout                               │");
        System.out.println("│  0. Sair                                 │");
        System.out.println("└──────────────────────────────────────────┘");
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine().trim();
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private void waitEnter() {
        prompt("Pressione ENTER para continuar");
    }

    private String getReportTitle(String type) {
        return switch (type) {
            case "pollution" -> "RELATÓRIO DE POLUIÇÃO";
            case "flood" -> "ALERTA DE ENCHENTE";
            case "noise" -> "MAPA DE RUÍDO";
            case "uv" -> "ÍNDICE UV";
            case "air-quality" -> "QUALIDADE DO AR";
            default -> type.toUpperCase();
        };
    }

    private void stop() {
        System.out.println("\n[*] Encerrando cliente...");
        if (tcpClient != null) {
            tcpClient.disconnect();
        }
        if (udpChannel != null) {
            udpChannel.getSocket().close();
        }
        scanner.close();
        System.out.println("[OK] Até logo!");
    }

    public static void main(String[] args) {
        String clientId = args.length > 0 ? args[0] : "CLIENT_" + UUID.randomUUID().toString().substring(0, 3);
        String discoveryHost = args.length > 1 ? args[1] : "localhost";
        int discoveryPort = args.length > 2 ? Integer.parseInt(args[2]) : 4000;

        ClientApp app = new ClientApp(clientId, discoveryHost, discoveryPort);
        app.start();
    }
}
