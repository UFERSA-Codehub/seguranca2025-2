package com.project.server.datacenter;

import com.project.messageBus.MessageType;
import com.project.messageBus.tcp.TcpHandshakeMessage;
import com.project.messageBus.tcp.TcpDataMessage;
import com.project.security.CryptoProtocol;
import com.project.security.KeyManager;
import com.project.security.SessionKeys;
import com.project.security.DebugConfig;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ServidorTCP {
    private final int porta;
    private final BancoDados bancoDados;
    private ServerSocket serverSocket;
    private volatile boolean executando;
    private ExecutorService threadPool;
    private AtomicInteger conexoesAtivas;
    private AtomicInteger totalLeituras;

    public ServidorTCP(int porta, BancoDados bancoDados) {
        this.porta = porta;
        this.bancoDados = bancoDados;
        this.executando = false;
        this.threadPool = Executors.newCachedThreadPool();
        this.conexoesAtivas = new AtomicInteger(0);
        this.totalLeituras = new AtomicInteger(0);
    }

    public void iniciar() {
        try {
            // Inicializar RSA para handshake
            KeyManager.initRSA();
            serverSocket = new ServerSocket(porta);
            executando = true;

            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║          SERVIDOR TCP DATACENTER INICIADO                      ║");
            System.out.println("╠════════════════════════════════════════════════════════════════╣");
            System.out.printf("║  Porta: %-55d║%n", porta);
            System.out.println("║  Aguardando conexões de Edge Servers...                        ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");

            while (executando) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    conexoesAtivas.incrementAndGet();
                    
                    String clienteInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                    System.out.println("\n[ServidorTCP] 🔌 Nova conexão: " + clienteInfo);
                    
                    threadPool.execute(() -> tratarCliente(clientSocket, clienteInfo));
                    
                } catch (SocketException e) {
                    if (executando) {
                        System.err.println("[ServidorTCP] ❌ Erro ao aceitar conexão: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("[ServidorTCP] ❌ Erro ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void tratarCliente(Socket socket, String clienteInfo) {
        SessionKeys sessionKeys = null;
        String edgeId = null;

        try (
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            // ═══════════════════════════════════════════════════════════════
            // FASE 1: HANDSHAKE - Estabelecer canal seguro
            // ═══════════════════════════════════════════════════════════════
            
            HandshakeResult result = realizarHandshake(in, out, clienteInfo);
            if (result == null) {
                System.err.println("[ServidorTCP] ❌ Handshake falhou com " + clienteInfo);
                return;
            }

            sessionKeys = result.sessionKeys;
            edgeId = result.edgeId;
            
            // Registrar SessionKeys recebidas do Edge no KeyManager
            KeyManager.registrarSessaoExterna(edgeId, sessionKeys);
            
            System.out.println("[ServidorTCP] ✅ Canal seguro estabelecido com " + clienteInfo + " (Edge: " + edgeId + ")");

            // ═══════════════════════════════════════════════════════════════
            // FASE 2: TRANSFERÊNCIA DE DADOS - Receber batches criptografados
            // ═══════════════════════════════════════════════════════════════
            
            while (executando && !socket.isClosed()) {
                int tamanho = in.readInt();
                if (tamanho <= 0 || tamanho > 1_000_000) { // Max 1MB por mensagem
                    System.err.println("[ServidorTCP] ⚠️  Tamanho inválido: " + tamanho);
                    break;
                }

                byte[] dadosCriptografados = new byte[tamanho];
                in.readFully(dadosCriptografados);

                processarMensagemDados(dadosCriptografados, sessionKeys, edgeId);
            }

        } catch (EOFException e) {
            System.out.println("[ServidorTCP] 🔌 Cliente " + clienteInfo + " desconectou");
            
        } catch (IOException e) {
            System.err.println("[ServidorTCP] ❌ Erro na comunicação com " + clienteInfo + ": " + e.getMessage());
            
        } finally {
            conexoesAtivas.decrementAndGet();
            if (edgeId != null) {
                KeyManager.removerChavesDaSessao(edgeId);
            }
            
            try {
                socket.close();
            } catch (IOException e) {
                // Ignorar
            }
            
            System.out.println("[ServidorTCP] 📊 Estatísticas - Conexões ativas: " + 
                conexoesAtivas.get() + " | Total leituras: " + totalLeituras.get());
        }
    }

    private static class HandshakeResult {
        SessionKeys sessionKeys;
        String edgeId;
        
        HandshakeResult(SessionKeys keys, String id) {
            this.sessionKeys = keys;
            this.edgeId = id;
        }
    }

    private HandshakeResult realizarHandshake(DataInputStream in, DataOutputStream out, String clienteInfo) {
        try {
            // ─────────────────────────────────────────────────────────────
            // PASSO 1: Receber HELLO do Edge Server
            // ─────────────────────────────────────────────────────────────
            
            int tamanhoHello = in.readInt();
            byte[] dadosHello = new byte[tamanhoHello];
            in.readFully(dadosHello);
            
            TcpHandshakeMessage helloMsg = TcpHandshakeMessage.deserialize(dadosHello);
            if (helloMsg == null || helloMsg.getType() != MessageType.TCP_HELLO) {
                enviarErro(out, "Esperava TCP_HELLO, recebeu " + (helloMsg == null ? "null" : helloMsg.getType()));
                return null;
            }

            String edgeId = helloMsg.getEdgeId();

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[ServidorTCP] 📩 HELLO recebido de " + clienteInfo + " (EdgeId: " + edgeId + ")");
            }

            // ─────────────────────────────────────────────────────────────
            // PASSO 2: Enviar CHALLENGE (chave pública RSA do Datacenter)
            // ─────────────────────────────────────────────────────────────
            
            String publicKeyBase64 = KeyManager.getChavePublicaRSABase64();
            TcpHandshakeMessage challengeMsg = TcpHandshakeMessage.createChallenge(publicKeyBase64);
            byte[] challengeBytes = challengeMsg.serialize();
            
            out.writeInt(challengeBytes.length);
            out.write(challengeBytes);
            out.flush();

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[ServidorTCP] 📤 CHALLENGE enviado para " + clienteInfo);
            }

            // ─────────────────────────────────────────────────────────────
            // PASSO 3: Receber KEY_EXCHANGE (SessionKeys criptografadas)
            // ─────────────────────────────────────────────────────────────
            
            int tamanhoKeyExchange = in.readInt();
            byte[] dadosKeyExchange = new byte[tamanhoKeyExchange];
            in.readFully(dadosKeyExchange);
            
            TcpHandshakeMessage keyExchangeMsg = TcpHandshakeMessage.deserialize(dadosKeyExchange);
            if (keyExchangeMsg == null || keyExchangeMsg.getType() != MessageType.TCP_KEY_EXCHANGE) {
                enviarErro(out, "Esperava TCP_KEY_EXCHANGE, recebeu " + (keyExchangeMsg == null ? "null" : keyExchangeMsg.getType()));
                return null;
            }

            // Extrair SessionKeys usando nossa chave privada RSA
            SessionKeys sessionKeys = keyExchangeMsg.extractSessionKeys(KeyManager.getRSALocal().getChavePrivada());
            
            if (sessionKeys == null) {
                enviarErro(out, "Falha ao extrair SessionKeys");
                return null;
            }

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[ServidorTCP] 📩 KEY_EXCHANGE recebido de " + clienteInfo);
                System.out.println("[ServidorTCP] ✅ SessionKeys extraídas com sucesso");
            }

            // ─────────────────────────────────────────────────────────────
            // PASSO 4: Enviar ACK (handshake completo)
            // ─────────────────────────────────────────────────────────────
            
            TcpHandshakeMessage ackMsg = TcpHandshakeMessage.createAck();
            byte[] ackBytes = ackMsg.serialize();
            
            out.writeInt(ackBytes.length);
            out.write(ackBytes);
            out.flush();

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[ServidorTCP] 📤 ACK enviado para " + clienteInfo);
            }

            return new HandshakeResult(sessionKeys, edgeId);

        } catch (Exception e) {
            System.err.println("[ServidorTCP] ❌ Erro no handshake: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
            try {
                enviarErro(out, "Erro interno: " + e.getMessage());
            } catch (IOException ignored) {}
            return null;
        }
    }

    private void enviarErro(DataOutputStream out, String mensagem) throws IOException {
        TcpHandshakeMessage errorMsg = TcpHandshakeMessage.createError(mensagem);
        byte[] errorBytes = errorMsg.serialize();
        out.writeInt(errorBytes.length);
        out.write(errorBytes);
        out.flush();
    }

    private void processarMensagemDados(byte[] dadosCriptografados, SessionKeys sessionKeys, String edgeId) {
        try {
            // Descriptografar mensagem usando CryptoProtocol
            byte[] dadosDescriptografados = CryptoProtocol.decryptAES_HMAC(dadosCriptografados, sessionKeys);

            // Desserializar JSON
            String json = new String(dadosDescriptografados, java.nio.charset.StandardCharsets.UTF_8);
            TcpDataMessage dataMsg = TcpDataMessage.deserializeFromString(json);
            
            if (dataMsg == null) {
                System.err.println("[ServidorTCP] ⚠️  Erro ao desserializar mensagem de dados");
                return;
            }
            
            if (dataMsg.getType() != MessageType.TCP_DATA_BATCH) {
                System.err.println("[ServidorTCP] ⚠️  Tipo de mensagem inesperado: " + dataMsg.getType());
                return;
            }

            // Obter leituras como LeituraInfo
            var leituras = dataMsg.getLeituras();

            // Inserir todas as leituras no banco de dados
            for (var leituraInfo : leituras) {
                var dadosAmbientais = leituraInfo.toDadosAmbientais();
                bancoDados.inserirLeituraComSensor(leituraInfo.sensorId, dadosAmbientais);
                totalLeituras.incrementAndGet();
            }

            if (DebugConfig.DEBUG_MODE) {
                System.out.printf("[ServidorTCP] 💾 Batch recebido de Edge %s: %d leituras salvas%n",
                    edgeId, leituras.size());
            } else {
                System.out.printf("[ServidorTCP] 💾 Batch: %d leituras de %s → Total: %d%n",
                    leituras.size(), edgeId, totalLeituras.get());
            }

        } catch (Exception e) {
            System.err.println("[ServidorTCP] ❌ Erro ao processar dados: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
        }
    }

    public void parar() {
        executando = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[ServidorTCP] Erro ao fechar socket: " + e.getMessage());
        }

        threadPool.shutdown();
        System.out.println("\n[ServidorTCP] 🛑 Servidor TCP encerrado");
    }

    public int getConexoesAtivas() {
        return conexoesAtivas.get();
    }

    public int getTotalLeituras() {
        return totalLeituras.get();
    }
}
