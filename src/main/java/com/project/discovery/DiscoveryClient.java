package com.project.discovery;

import com.project.messageBus.MessageType;
import com.project.messageBus.udp.DiscoveryMessage;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class DiscoveryClient {
    
    private String servidorHost;
    private int servidorPorta;
    private static final int TIMEOUT_MS = 5000;
    private static final int TAMANHO_BUFFER = 1024;

    public DiscoveryClient() {
        this("127.0.0.1", 4000);
    }

    public DiscoveryClient(String servidorHost, int servidorPorta) {
        this.servidorHost = servidorHost;
        this.servidorPorta = servidorPorta;
    }

    public boolean registrarEdge(String host, int porta) {
        try {
            DiscoveryMessage requisicao = DiscoveryMessage.registrarEdge(host, porta);
            DiscoveryMessage resposta = enviarEReceber(requisicao);
            
            if (resposta != null && resposta.getType() == MessageType.DISCOVERY_RESPONSE_EDGE) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("[DiscoveryClient] Erro ao registrar Edge: " + e.getMessage());
            return false;
        }
    }

    public boolean registrarDatacenter(String host, int porta) {
        try {
            DiscoveryMessage requisicao = DiscoveryMessage.registrarDatacenter(host, porta);
            DiscoveryMessage resposta = enviarEReceber(requisicao);
            
            if (resposta != null && resposta.getType() == MessageType.DISCOVERY_RESPONSE_DATACENTER) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("[DiscoveryClient] Erro ao registrar Datacenter: " + e.getMessage());
            return false;
        }
    }

    public ServiceInfo descobrirEdge() {
        try {
            DiscoveryMessage requisicao = DiscoveryMessage.descobrirEdge();
            DiscoveryMessage resposta = enviarEReceber(requisicao);
            
            if (resposta != null && resposta.getType() == MessageType.DISCOVERY_RESPONSE_EDGE) {
                return new ServiceInfo("EDGE", resposta.getHost(), resposta.getPorta());
            }
            
            if (resposta != null && resposta.getType() == MessageType.DISCOVERY_ERROR) {
                System.err.println("[DiscoveryClient] Edge não encontrado: " + resposta.getErro());
            }
            
            return null;
            
        } catch (Exception e) {
            System.err.println("[DiscoveryClient] Erro ao descobrir Edge: " + e.getMessage());
            return null;
        }
    }

    public ServiceInfo descobrirDatacenter() {
        try {
            DiscoveryMessage requisicao = DiscoveryMessage.descobrirDatacenter();
            DiscoveryMessage resposta = enviarEReceber(requisicao);
            
            if (resposta != null && resposta.getType() == MessageType.DISCOVERY_RESPONSE_DATACENTER) {
                return new ServiceInfo("DATACENTER", resposta.getHost(), resposta.getPorta());
            }
            
            if (resposta != null && resposta.getType() == MessageType.DISCOVERY_ERROR) {
                System.err.println("[DiscoveryClient] Datacenter não encontrado: " + resposta.getErro());
            }
            
            return null;
            
        } catch (Exception e) {
            System.err.println("[DiscoveryClient] Erro ao descobrir Datacenter: " + e.getMessage());
            return null;
        }
    }

    private DiscoveryMessage enviarEReceber(DiscoveryMessage mensagem) throws Exception {
        DatagramSocket socket = null;
        
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            
            byte[] dadosEnvio = mensagem.toBytes();
            InetAddress endereco = InetAddress.getByName(servidorHost);
            DatagramPacket pacoteEnvio = new DatagramPacket(
                dadosEnvio, 
                dadosEnvio.length, 
                endereco, 
                servidorPorta
            );
            socket.send(pacoteEnvio);
            
            byte[] bufferRecebimento = new byte[TAMANHO_BUFFER];
            DatagramPacket pacoteRecebimento = new DatagramPacket(bufferRecebimento, bufferRecebimento.length);
            
            try {
                socket.receive(pacoteRecebimento);
                
                byte[] dadosRecebidos = new byte[pacoteRecebimento.getLength()];
                System.arraycopy(pacoteRecebimento.getData(), 0, dadosRecebidos, 0, pacoteRecebimento.getLength());
                
                return DiscoveryMessage.fromBytes(dadosRecebidos);
                
            } catch (SocketTimeoutException e) {
                throw new Exception("Timeout ao aguardar resposta do servidor de descoberta");
            }
            
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public boolean testarConexao() {
        try {
            DiscoveryMessage requisicao = DiscoveryMessage.descobrirEdge();
            DiscoveryMessage resposta = enviarEReceber(requisicao);
            return resposta != null;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    public String getServidorHost() { return servidorHost; }
    public int getServidorPorta() { return servidorPorta; }
}
