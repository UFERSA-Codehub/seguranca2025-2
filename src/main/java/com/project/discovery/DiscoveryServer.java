package com.project.discovery;

import com.project.messageBus.udp.DiscoveryMessage;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DiscoveryServer implements Runnable {
    
    private int porta;
    private DatagramSocket socket;
    private boolean executando;
    private Thread thread;
    
    // Listas thread-safe de serviços registrados
    private List<InfoServico> edgeServers;
    private List<InfoServico> datacenters;
    
    private static final int TIMEOUT_HEARTBEAT_MS = 60000; // 60 segundos
    private static final int TAMANHO_BUFFER = 1024;
    
    public DiscoveryServer(int porta) {
        this.porta = porta;
        this.executando = false;
        this.edgeServers = new CopyOnWriteArrayList<>();
        this.datacenters = new CopyOnWriteArrayList<>();
    }

    public void iniciar() {
        if (executando) {
            System.err.println("[DiscoveryServer] Servidor já está em execução!");
            return;
        }
        
        try {
            socket = new DatagramSocket(porta);
            executando = true;
            thread = new Thread(this, "DiscoveryServer");
            thread.start();
            
            System.out.println("[DiscoveryServer] 🌐 Servidor de Localização iniciado na porta " + porta);
            System.out.println("[DiscoveryServer] 📝 Aguardando registro de serviços e requisições de descoberta...\n");
            
        } catch (Exception e) {
            System.err.println("[DiscoveryServer] Erro ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
            executando = false;
        }
    }

    public void parar() {
        executando = false;
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("[DiscoveryServer] 🛑 Servidor de Localização parado.");
    }
    
    @Override
    public void run() {
        byte[] buffer = new byte[TAMANHO_BUFFER];
        
        while (executando) {
            try {
                // Receber pacote UDP
                DatagramPacket pacoteRecebido = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacoteRecebido);
                
                // Processar mensagem em thread separada para não bloquear
                processarMensagem(pacoteRecebido);
                
            } catch (Exception e) {
                if (executando) {
                    System.err.println("[DiscoveryServer] Erro ao receber pacote: " + e.getMessage());
                }
            }
        }
        
        System.out.println("[DiscoveryServer] Thread principal encerrada.");
    }

    private void processarMensagem(DatagramPacket pacote) {
        try {
            // Deserializar mensagem
            byte[] dados = new byte[pacote.getLength()];
            System.arraycopy(pacote.getData(), 0, dados, 0, pacote.getLength());
            DiscoveryMessage mensagem = DiscoveryMessage.deserializeFromBytes(dados);
            
            InetAddress enderecoRemetente = pacote.getAddress();
            int portaRemetente = pacote.getPort();
            
            // Processar de acordo com o tipo
            DiscoveryMessage resposta = null;
            
            switch (mensagem.getType()) {
                case DISCOVERY_REGISTER_EDGE:
                    resposta = processarRegistroEdge(mensagem, enderecoRemetente);
                    break;
                    
                case DISCOVERY_REGISTER_DATACENTER:
                    resposta = processarRegistroDatacenter(mensagem, enderecoRemetente);
                    break;
                    
                case DISCOVERY_FIND_EDGE:
                    resposta = processarDescobertaEdge();
                    break;
                    
                case DISCOVERY_FIND_DATACENTER:
                    resposta = processarDescobertaDatacenter();
                    break;
                    
                case DISCOVERY_HEARTBEAT:
                    // Atualizar heartbeat (não precisa responder)
                    break;
                    
                default:
                    resposta = DiscoveryMessage.erro("Tipo de mensagem não suportado");
                    break;
            }
            
            // Enviar resposta se houver
            if (resposta != null) {
                byte[] dadosResposta = resposta.toBytes();
                DatagramPacket pacoteResposta = new DatagramPacket(
                    dadosResposta, 
                    dadosResposta.length, 
                    enderecoRemetente, 
                    portaRemetente
                );
                socket.send(pacoteResposta);
            }
            
        } catch (Exception e) {
            System.err.println("[DiscoveryServer] Erro ao processar mensagem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private DiscoveryMessage processarRegistroEdge(DiscoveryMessage mensagem, InetAddress enderecoRemetente) {
        String host = mensagem.getHost();
        if (host == null || host.equals("0.0.0.0")) {
            host = enderecoRemetente.getHostAddress();
        }
        
        int porta = mensagem.getPorta();
        
        // Verificar se já existe
        InfoServico existente = buscarEdge(host, porta);
        if (existente != null) {
            existente.atualizarHeartbeat();
            System.out.println("[DiscoveryServer] 🔄 Edge Server atualizado: " + host + ":" + porta);
        } else {
            InfoServico novoEdge = new InfoServico("EDGE", host, porta);
            edgeServers.add(novoEdge);
            System.out.println("[DiscoveryServer] ✅ Edge Server registrado: " + host + ":" + porta);
        }
        
        // Retornar confirmação
        return DiscoveryMessage.respostaEdge(host, porta);
    }

    private DiscoveryMessage processarRegistroDatacenter(DiscoveryMessage mensagem, InetAddress enderecoRemetente) {
        String host = mensagem.getHost();
        if (host == null || host.equals("0.0.0.0")) {
            host = enderecoRemetente.getHostAddress();
        }
        
        int porta = mensagem.getPorta();
        
        // Verificar se já existe
        InfoServico existente = buscarDatacenter(host, porta);
        if (existente != null) {
            existente.atualizarHeartbeat();
            System.out.println("[DiscoveryServer] 🔄 Datacenter atualizado: " + host + ":" + porta);
        } else {
            InfoServico novoDatacenter = new InfoServico("DATACENTER", host, porta);
            datacenters.add(novoDatacenter);
            System.out.println("[DiscoveryServer] ✅ Datacenter registrado: " + host + ":" + porta);
        }
        
        // Retornar confirmação
        return DiscoveryMessage.respostaDatacenter(host, porta);
    }

    private DiscoveryMessage processarDescobertaEdge() {
        // Remover serviços inativos
        // COMENTADO: Edge Servers não enviam heartbeat periódico
        // edgeServers.removeIf(edge -> !edge.estaAtivo(TIMEOUT_HEARTBEAT_MS));

        if (edgeServers.isEmpty()) {
            System.out.println("[DiscoveryServer] ⚠️  Nenhum Edge Server disponível");
            return DiscoveryMessage.erro("Nenhum Edge Server disponível");
        }
        
        // Retornar o primeiro disponível (pode implementar load balancing aqui)
        InfoServico edge = edgeServers.get(0);
        System.out.println("[DiscoveryServer] 🔍 Descoberta Edge → " + edge.getHost() + ":" + edge.getPorta());
        return DiscoveryMessage.respostaEdge(edge.getHost(), edge.getPorta());
    }

    private DiscoveryMessage processarDescobertaDatacenter() {
        // Remover serviços inativos
        // COMENTADO: Datacenters não enviam heartbeat periódico
        // datacenters.removeIf(dc -> !dc.estaAtivo(TIMEOUT_HEARTBEAT_MS));

        if (datacenters.isEmpty()) {
            System.out.println("[DiscoveryServer] ⚠️  Nenhum Datacenter disponível");
            return DiscoveryMessage.erro("Nenhum Datacenter disponível");
        }
        
        // Retornar o primeiro disponível (pode implementar load balancing aqui)
        InfoServico datacenter = datacenters.get(0);
        System.out.println("[DiscoveryServer] 🔍 Descoberta Datacenter → " + datacenter.getHost() + ":" + datacenter.getPorta());
        return DiscoveryMessage.respostaDatacenter(datacenter.getHost(), datacenter.getPorta());
    }

    private InfoServico buscarEdge(String host, int porta) {
        for (InfoServico edge : edgeServers) {
            if (edge.getHost().equals(host) && edge.getPorta() == porta) {
                return edge;
            }
        }
        return null;
    }

    private InfoServico buscarDatacenter(String host, int porta) {
        for (InfoServico dc : datacenters) {
            if (dc.getHost().equals(host) && dc.getPorta() == porta) {
                return dc;
            }
        }
        return null;
    }

    public void exibirStatus() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║       STATUS DO SERVIDOR DE LOCALIZAÇÃO                        ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        
        System.out.println("║  Edge Servers registrados: " + edgeServers.size() + "                                 ║");
        for (InfoServico edge : edgeServers) {
            String status = edge.estaAtivo(TIMEOUT_HEARTBEAT_MS) ? "🟢 ATIVO" : "🔴 INATIVO";
            System.out.printf("║    %s %s:%-5d                                       ║%n",
                status, edge.getHost(), edge.getPorta());
        }
        
        System.out.println("║                                                                ║");
        System.out.println("║  Datacenters registrados: " + datacenters.size() + "                                  ║");
        for (InfoServico dc : datacenters) {
            String status = dc.estaAtivo(TIMEOUT_HEARTBEAT_MS) ? "🟢 ATIVO" : "🔴 INATIVO";
            System.out.printf("║    %s %s:%-5d                                       ║%n",
                status, dc.getHost(), dc.getPorta());
        }
        
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
    }
    
    // Getters
    public boolean isExecutando() { return executando; }
    public List<InfoServico> getEdgeServers() { return new ArrayList<>(edgeServers); }
    public List<InfoServico> getDatacenters() { return new ArrayList<>(datacenters); }
}
