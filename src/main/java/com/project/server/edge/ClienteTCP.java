package com.project.server.edge;

import com.project.messageBus.MessageType;
import com.project.messageBus.tcp.TcpHandshakeMessage;
import com.project.messageBus.tcp.TcpDataMessage;
import com.project.security.KeyManager;
import com.project.security.SessionKeys;
import com.project.security.DebugConfig;

import java.io.*;
import java.net.Socket;
import java.security.PublicKey;

public class ClienteTCP {
    private final String host;
    private final int porta;
    private final String edgeId;
    
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private SessionKeys sessionKeys;
    private boolean conectado;

    public ClienteTCP(String host, int porta, String edgeId) {
        this.host = host;
        this.porta = porta;
        this.edgeId = edgeId;
        this.conectado = false;
    }

    public boolean conectar() {
        try {
            // Inicializar RSA local
            KeyManager.initRSA();
            
            // Conectar ao Datacenter
            socket = new Socket(host, porta);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            
            System.out.println("[ClienteTCP] 🔌 Conectado ao Datacenter: " + host + ":" + porta);
            
            // Realizar handshake
            if (!realizarHandshake()) {
                System.err.println("[ClienteTCP] ❌ Falha no handshake");
                desconectar();
                return false;
            }
            
            conectado = true;
            System.out.println("[ClienteTCP] ✅ Handshake concluído - Canal seguro estabelecido");
            return true;
            
        } catch (IOException e) {
            System.err.println("[ClienteTCP] ❌ Erro ao conectar: " + e.getMessage());
            return false;
        }
    }

    private boolean realizarHandshake() {
        try {
            // ─────────────────────────────────────────────────────────────
            // PASSO 1: Enviar HELLO
            // ─────────────────────────────────────────────────────────────
            
            TcpHandshakeMessage helloMsg = TcpHandshakeMessage.createHello(edgeId);
            byte[] helloBytes = helloMsg.serialize();
            
            out.writeInt(helloBytes.length);
            out.write(helloBytes);
            out.flush();

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[ClienteTCP] 📤 HELLO enviado (EdgeId: " + edgeId + ")");
            }

            // ─────────────────────────────────────────────────────────────
            // PASSO 2: Receber CHALLENGE (chave pública RSA do Datacenter)
            // ─────────────────────────────────────────────────────────────
            
            int tamanhoChallenge = in.readInt();
            byte[] dadosChallenge = new byte[tamanhoChallenge];
            in.readFully(dadosChallenge);
            
            TcpHandshakeMessage challengeMsg = TcpHandshakeMessage.deserialize(dadosChallenge);
            if (challengeMsg == null || challengeMsg.getType() != MessageType.TCP_CHALLENGE) {
                System.err.println("[ClienteTCP] ❌ Resposta inválida - esperava CHALLENGE");
                return false;
            }

            PublicKey datacenterPublicKey = challengeMsg.getPublicKey();
            if (datacenterPublicKey == null) {
                System.err.println("[ClienteTCP] ❌ Chave pública do Datacenter inválida");
                return false;
            }

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[ClienteTCP] 📩 CHALLENGE recebido - chave pública obtida");
            }

            // ─────────────────────────────────────────────────────────────
            // PASSO 3: Criar SessionKeys e enviar KEY_EXCHANGE
            // ─────────────────────────────────────────────────────────────
            
            // Criar novas chaves de sessão
            sessionKeys = KeyManager.criarChavesDaSessao(edgeId);
            
            // Criar mensagem KEY_EXCHANGE com as SessionKeys cifradas
            TcpHandshakeMessage keyExchangeMsg = TcpHandshakeMessage.createKeyExchange(
                sessionKeys, 
                datacenterPublicKey
            );
            byte[] keyExchangeBytes = keyExchangeMsg.serialize();
            
            out.writeInt(keyExchangeBytes.length);
            out.write(keyExchangeBytes);
            out.flush();

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[ClienteTCP] 📤 KEY_EXCHANGE enviado - SessionKeys cifradas");
            }

            // ─────────────────────────────────────────────────────────────
            // PASSO 4: Receber ACK
            // ─────────────────────────────────────────────────────────────
            
            int tamanhoAck = in.readInt();
            byte[] dadosAck = new byte[tamanhoAck];
            in.readFully(dadosAck);
            
            TcpHandshakeMessage ackMsg = TcpHandshakeMessage.deserialize(dadosAck);
            if (ackMsg == null) {
                System.err.println("[ClienteTCP] ❌ Falha ao desserializar resposta do Datacenter");
                return false;
            }
            
            if (ackMsg.getType() == MessageType.TCP_ERROR) {
                System.err.println("[ClienteTCP] ❌ Erro do Datacenter: " + ackMsg.getErrorMessage());
                return false;
            }
            
            if (ackMsg.getType() != MessageType.TCP_ACK) {
                System.err.println("[ClienteTCP] ❌ Resposta inválida - esperava ACK");
                return false;
            }

            if (DebugConfig.DEBUG_MODE) {
                System.out.println("[ClienteTCP] 📩 ACK recebido - handshake completo");
            }

            return true;

        } catch (Exception e) {
            System.err.println("[ClienteTCP] ❌ Erro no handshake: " + e.getMessage());
            if (DebugConfig.DEBUG_MODE) {
                e.printStackTrace();
            }
            return false;
        }
    }

    public boolean enviarBatch(TcpDataMessage dataMsg) {
        if (!conectado) {
            System.err.println("[ClienteTCP] ❌ Não conectado ao Datacenter");
            return false;
        }

        try {
            // Criptografar mensagem usando SessionKeys
            byte[] dadosCriptografados = dataMsg.encrypt(sessionKeys);
            
            // Enviar tamanho + dados criptografados
            out.writeInt(dadosCriptografados.length);
            out.write(dadosCriptografados);
            out.flush();

            if (DebugConfig.DEBUG_MODE) {
                System.out.printf("[ClienteTCP] 📤 Batch enviado: %d leituras (%d bytes criptografados)%n",
                    dataMsg.quantidadeLeituras(), dadosCriptografados.length);
            }

            return true;

        } catch (IOException e) {
            System.err.println("[ClienteTCP] ❌ Erro ao enviar batch: " + e.getMessage());
            conectado = false;
            return false;
        } catch (Exception e) {
            System.err.println("[ClienteTCP] ❌ Erro ao criptografar batch: " + e.getMessage());
            return false;
        }
    }

    public void desconectar() {
        conectado = false;
        
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[ClienteTCP] Erro ao desconectar: " + e.getMessage());
        }
        
        if (edgeId != null) {
            KeyManager.removerChavesDaSessao(edgeId);
        }
        
        System.out.println("[ClienteTCP] 🔌 Desconectado do Datacenter");
    }

    public boolean isConectado() {
        return conectado && socket != null && !socket.isClosed();
    }

    public boolean reconectar() {
        if (isConectado()) {
            return true;
        }
        
        System.out.println("[ClienteTCP] 🔄 Tentando reconectar ao Datacenter...");
        desconectar(); // Limpar conexão anterior
        return conectar();
    }

    public String getEdgeId() {
        return edgeId;
    }

    public String getHost() {
        return host;
    }

    public int getPorta() {
        return porta;
    }
}
