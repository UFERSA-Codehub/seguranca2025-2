package com.project.sensors;

import com.project.model.Sensor;

public class SensorApp {
    
    private static final String DEFAULT_EDGE_HOST = "127.0.0.1";
    private static final int DEFAULT_EDGE_PORT = 5000;
    
    public static void main(String[] args) {
        // Validar argumentos
        if (args.length < 4) {
            exibirUso();
            System.exit(1);
        }
        
        String sensorId = args[0];
        String nome = args[1];
        String localizacao = args[2];
        String token = args[3];
        String edgeHost = args.length > 4 ? args[4] : DEFAULT_EDGE_HOST;
        int edgePort = args.length > 5 ? Integer.parseInt(args[5]) : DEFAULT_EDGE_PORT;
        
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║              SENSOR IoT - PROCESSO INDEPENDENTE                ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  ID:          %-49s ║%n", sensorId);
        System.out.printf("║  Nome:        %-49s ║%n", nome);
        System.out.printf("║  Localização: %-49s ║%n", localizacao);
        System.out.printf("║  Edge Server: %-49s ║%n", edgeHost + ":" + edgePort);
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
        
        // Criar sensor
        Sensor sensor = new Sensor(sensorId, nome, localizacao, token);
        
        // Criar dispositivo com senha (token) para autenticação
        DispositivoSensor dispositivo = new DispositivoSensor(
            sensor, 
            edgeHost, 
            edgePort,
            token,  // Senha/token para autenticação JWT
            2000,   // intervalo mínimo: 2 segundos
            3000    // intervalo máximo: 3 segundos
        );
        
        // Registrar hook para parada graceful
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SensorApp] 🛑 Recebido sinal de parada. Encerrando sensor " + sensorId + "...");
            dispositivo.parar();
            System.out.println("[SensorApp] ✅ Sensor " + sensorId + " encerrado com sucesso.");
        }));
        
        // Iniciar sensor
        System.out.println("[SensorApp] 🚀 Iniciando sensor " + sensorId + "...\n");
        dispositivo.iniciar();
        
        System.out.println("[SensorApp] ✅ Sensor " + sensorId + " em execução.");
        System.out.println("[SensorApp] ℹ️  Pressione Ctrl+C para parar o sensor.\n");
        
        // Manter processo vivo
        while (dispositivo.isExecutando()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("\n[SensorApp] ⚠️  Sensor " + sensorId + " interrompido.");
                break;
            }
        }
        
        // Garantir parada se não foi pelo shutdown hook
        if (dispositivo.isExecutando()) {
            dispositivo.parar();
        }
        
        System.out.println("[SensorApp] 👋 Processo do sensor " + sensorId + " finalizado.");
    }
    
    private static void exibirUso() {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    SENSOR APP - USO                            ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Argumentos obrigatórios:                                      ║");
        System.out.println("║    1. sensorId     - ID único do sensor (ex: SENSOR_001)       ║");
        System.out.println("║    2. nome         - Nome descritivo (ex: \"Sensor Parque\")   ║");
        System.out.println("║    3. localizacao  - Local (ex: \"Parque Central\")            ║");
        System.out.println("║    4. token        - Token de autenticação                     ║");
        System.out.println("║                                                                ║");
        System.out.println("║  Argumentos opcionais:                                         ║");
        System.out.println("║    5. edgeHost     - Host do Edge Server (padrão: 127.0.0.1)   ║");
        System.out.println("║    6. edgePort     - Porta do Edge Server (padrão: 5000)       ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Exemplo:                                                      ║");
        System.out.println("║                                                                ║");
        System.out.println("║  java -cp target/classes com.project.dispositivo.SensorApp \\  ║");
        System.out.println("║       SENSOR_001 \\                                            ║");
        System.out.println("║       \"Sensor Parque Central\" \\                             ║");
        System.out.println("║       \"Parque Central\" \\                                    ║");
        System.out.println("║       TOKEN_SENSOR_001_8f3a9b2c \\                             ║");
        System.out.println("║       localhost \\                                             ║");
        System.out.println("║       5000                                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
    }
}
