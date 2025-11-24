package com.project.messageBus.udp;

import com.project.messageBus.Message;
import com.project.messageBus.MessageType;
import java.nio.charset.StandardCharsets;

public class DiscoveryMessage extends Message {
    
    private static final long serialVersionUID = 1L;
    
    private String host;
    private int porta;
    private String erro;

    public DiscoveryMessage(MessageType tipo, String host, int porta) {
        super(tipo);
        
        if (!tipo.isDiscovery()) {
            throw new IllegalArgumentException("Tipo deve ser DISCOVERY_*");
        }
        
        this.host = host;
        this.porta = porta;
    }

    public DiscoveryMessage(MessageType tipo) {
        super(tipo);
        
        if (!tipo.isDiscovery()) {
            throw new IllegalArgumentException("Tipo deve ser DISCOVERY_*");
        }
    }

    public DiscoveryMessage(MessageType tipo, String host, int porta, long timestamp) {
        super(tipo, timestamp);
        
        if (!tipo.isDiscovery()) {
            throw new IllegalArgumentException("Tipo deve ser DISCOVERY_*");
        }
        
        this.host = host;
        this.porta = porta;
    }
    
    // Getters
    public String getHost() {
        return host;
    }
    
    public int getPorta() {
        return porta;
    }
    
    public String getErro() {
        return erro;
    }
    
    // Setter para erro
    public void setErro(String erro) {
        this.erro = erro;
    }

    private String serializeToString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name());
        sb.append("||");
        sb.append(timestamp);
        
        if (host != null) {
            sb.append("||");
            sb.append(host);
        } else {
            sb.append("||");
        }
        
        if (porta > 0) {
            sb.append("||");
            sb.append(porta);
        } else {
            sb.append("||0");
        }
        
        if (erro != null) {
            sb.append("||");
            sb.append(erro);
        }
        
        return sb.toString();
    }

    @Override
    public byte[] serialize() {
        return serializeToString().getBytes(StandardCharsets.UTF_8);
    }

    public static DiscoveryMessage deserializeFromString(String data) {
        try {
            String[] parts = data.split("\\|\\|");
            
            if (parts.length < 4) {
                throw new IllegalArgumentException("Formato de mensagem inválido: campos insuficientes");
            }
            
            MessageType tipo = MessageType.valueOf(parts[0]);
            long timestamp = Long.parseLong(parts[1]);
            String host = parts[2].isEmpty() ? null : parts[2];
            int porta = Integer.parseInt(parts[3]);
            
            DiscoveryMessage msg = new DiscoveryMessage(tipo, host, porta, timestamp);
            
            // Erro é opcional (5º campo)
            if (parts.length > 4 && !parts[4].isEmpty()) {
                msg.setErro(parts[4]);
            }
            
            return msg;
            
        } catch (Exception e) {
            System.err.println("[DiscoveryMessage] Erro ao deserializar: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static DiscoveryMessage deserializeFromBytes(byte[] data) {
        String str = new String(data, StandardCharsets.UTF_8);
        return deserializeFromString(str);
    }
    
    @Override
    public String toJSON() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"tipo\":\"").append(type.name()).append("\"");
        json.append(",\"timestamp\":").append(timestamp);
        
        if (host != null) {
            json.append(",\"host\":\"").append(host).append("\"");
        }
        
        if (porta > 0) {
            json.append(",\"porta\":").append(porta);
        }
        
        if (erro != null) {
            json.append(",\"erro\":\"").append(erro).append("\"");
        }
        
        json.append("}");
        return json.toString();
    }
    
    @Override
    public void validate() throws IllegalStateException {
        if (!type.isDiscovery()) {
            throw new IllegalStateException("Tipo de mensagem inválido para DiscoveryMessage");
        }
        
        // Validações específicas por tipo
        switch (type) {
            case DISCOVERY_REGISTER_EDGE:
            case DISCOVERY_REGISTER_DATACENTER:
            case DISCOVERY_RESPONSE_EDGE:
            case DISCOVERY_RESPONSE_DATACENTER:
                if (host == null || host.isEmpty()) {
                    throw new IllegalStateException("Host é obrigatório para " + type);
                }
                if (porta <= 0) {
                    throw new IllegalStateException("Porta inválida para " + type);
                }
                break;
                
            case DISCOVERY_ERROR:
                if (erro == null || erro.isEmpty()) {
                    throw new IllegalStateException("Erro deve ser especificado para DISCOVERY_ERROR");
                }
                break;
                
            default:
                // DISCOVERY_FIND_* e DISCOVERY_HEARTBEAT não precisam de validações extras
                break;
        }
    }
    
    @Override
    public int getSize() {
        return serializeToString().length();
    }
    
    @Override
    public String toString() {
        return String.format("DiscoveryMessage{tipo=%s, host='%s', porta=%d, timestamp=%d}",
            type, host, porta, timestamp);
    }
    
    // ========== FACTORY METHODS (Compatibilidade com MensagemLocalizacao) ==========

    public static DiscoveryMessage registrarEdge(String host, int porta) {
        return new DiscoveryMessage(MessageType.DISCOVERY_REGISTER_EDGE, host, porta);
    }

    public static DiscoveryMessage registrarDatacenter(String host, int porta) {
        return new DiscoveryMessage(MessageType.DISCOVERY_REGISTER_DATACENTER, host, porta);
    }

    public static DiscoveryMessage descobrirEdge() {
        return new DiscoveryMessage(MessageType.DISCOVERY_FIND_EDGE);
    }

    public static DiscoveryMessage descobrirDatacenter() {
        return new DiscoveryMessage(MessageType.DISCOVERY_FIND_DATACENTER);
    }

    public static DiscoveryMessage respostaEdge(String host, int porta) {
        return new DiscoveryMessage(MessageType.DISCOVERY_RESPONSE_EDGE, host, porta);
    }

    public static DiscoveryMessage respostaDatacenter(String host, int porta) {
        return new DiscoveryMessage(MessageType.DISCOVERY_RESPONSE_DATACENTER, host, porta);
    }

    public static DiscoveryMessage heartbeat() {
        return new DiscoveryMessage(MessageType.DISCOVERY_HEARTBEAT);
    }

    public static DiscoveryMessage erro(String mensagemErro) {
        DiscoveryMessage msg = new DiscoveryMessage(MessageType.DISCOVERY_ERROR);
        msg.setErro(mensagemErro);
        return msg;
    }
    
    // ========== MÉTODOS DE COMPATIBILIDADE COM MensagemLocalizacao ==========

    public byte[] toBytes() {
        return serialize();
    }

    public static DiscoveryMessage fromBytes(byte[] dados) {
        return deserializeFromBytes(dados);
    }

    public static DiscoveryMessage fromJSON(String json) {
        // Se for formato JSON real, converter para string simples
        if (json.startsWith("{")) {
            // Parse simples de JSON (compatibilidade com formato antigo)
            try {
                String tipoStr = extrairCampo(json, "tipo");
                MessageType tipo = MessageType.valueOf(tipoStr);
                
                String host = extrairCampo(json, "host");
                String portaStr = extrairCampoNumerico(json, "porta");
                int porta = portaStr != null ? Integer.parseInt(portaStr) : 0;
                
                DiscoveryMessage msg = new DiscoveryMessage(tipo, host, porta);
                
                String erro = extrairCampo(json, "erro");
                if (erro != null) {
                    msg.setErro(erro);
                }
                
                return msg;
                
            } catch (Exception e) {
                throw new RuntimeException("Erro ao deserializar mensagem JSON: " + json, e);
            }
        } else {
            // Formato com separador ||
            return deserializeFromString(json);
        }
    }
    
    // Métodos auxiliares para parse de JSON (compatibilidade)
    private static String extrairCampo(String json, String campo) {
        String busca = "\"" + campo + "\":\"";
        int inicio = json.indexOf(busca);
        if (inicio == -1) return null;
        inicio += busca.length();
        int fim = json.indexOf("\"", inicio);
        return json.substring(inicio, fim);
    }
    
    private static String extrairCampoNumerico(String json, String campo) {
        String busca = "\"" + campo + "\":";
        int inicio = json.indexOf(busca);
        if (inicio == -1) return null;
        inicio += busca.length();
        int fim = json.indexOf(",", inicio);
        if (fim == -1) fim = json.indexOf("}", inicio);
        return json.substring(inicio, fim).trim();
    }
}
