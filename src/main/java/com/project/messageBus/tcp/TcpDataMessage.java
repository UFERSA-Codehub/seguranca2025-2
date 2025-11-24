package com.project.messageBus.tcp;

import com.project.messageBus.Message;
import com.project.messageBus.MessageType;
import com.project.messageBus.SecureMessage;
import com.project.model.DadosAmbientais;
import com.project.security.SessionKeys;
import com.project.security.CryptoProtocol;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TcpDataMessage extends Message implements SecureMessage {
    
    private static final long serialVersionUID = 1L;
    
    private List<LeituraInfo> leituras;

    public static class LeituraInfo implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        public String sensorId;
        public long timestamp;
        public String localizacao;
        public double temperatura;
        public double co2;
        public double umidade;
        public double ruido;
        public double radiacao_uv;
        public double pm25;
        public double pm10;
        
        public LeituraInfo() {}
        
        public LeituraInfo(String sensorId, DadosAmbientais dados) {
            this.sensorId = sensorId;
            this.timestamp = dados.getTimestamp();
            this.localizacao = dados.getLocalizacao();
            this.temperatura = dados.getTemperatura();
            this.co2 = dados.getCo2();
            this.umidade = dados.getUmidade();
            this.ruido = dados.getRuido();
            this.radiacao_uv = dados.getRadiacao_uv();
            this.pm25 = dados.getPm25();
            this.pm10 = dados.getPm10();
        }
        
        public DadosAmbientais toDadosAmbientais() {
            return new DadosAmbientais(timestamp, localizacao, temperatura, co2,
                    umidade, ruido, radiacao_uv, pm25, pm10);
        }
        
        @Override
        public String toString() {
            return String.format("LeituraInfo{sensor=%s, temp=%.1f, co2=%.1f, umid=%.1f, ts=%d}",
                    sensorId, temperatura, co2, umidade, timestamp);
        }
    }

    public TcpDataMessage() {
        super(MessageType.TCP_DATA_BATCH);
        this.leituras = new ArrayList<>();
    }

    public TcpDataMessage(List<LeituraInfo> leituras) {
        super(MessageType.TCP_DATA_BATCH);
        this.leituras = new ArrayList<>(leituras);
    }
    
    // Getters e Setters
    public List<LeituraInfo> getLeituras() {
        return new ArrayList<>(leituras);
    }
    
    public void setLeituras(List<LeituraInfo> leituras) {
        this.leituras = new ArrayList<>(leituras);
    }
    
    public void adicionarLeitura(String sensorId, DadosAmbientais dados) {
        leituras.add(new LeituraInfo(sensorId, dados));
    }
    
    public int quantidadeLeituras() {
        return leituras.size();
    }

    private String toJSONString() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"tipo\":\"").append(type.name()).append("\",");
        json.append("\"timestamp\":").append(timestamp).append(",");
        json.append("\"leituras\":[");
        
        for (int i = 0; i < leituras.size(); i++) {
            if (i > 0) json.append(",");
            LeituraInfo l = leituras.get(i);
            json.append("{");
            json.append("\"sensorId\":\"").append(l.sensorId).append("\",");
            json.append("\"timestamp\":").append(l.timestamp).append(",");
            json.append("\"localizacao\":\"").append(l.localizacao).append("\",");
            json.append("\"temperatura\":").append(l.temperatura).append(",");
            json.append("\"co2\":").append(l.co2).append(",");
            json.append("\"umidade\":").append(l.umidade).append(",");
            json.append("\"ruido\":").append(l.ruido).append(",");
            json.append("\"radiacao_uv\":").append(l.radiacao_uv).append(",");
            json.append("\"pm25\":").append(l.pm25).append(",");
            json.append("\"pm10\":").append(l.pm10);
            json.append("}");
        }
        
        json.append("]}");
        return json.toString();
    }
    
    @Override
    public byte[] serialize() {
        return toJSONString().getBytes(StandardCharsets.UTF_8);
    }

    public static TcpDataMessage deserializeFromString(String json) {
        TcpDataMessage msg = new TcpDataMessage();
        
        try {
            // Parse manual (simples) - sem dependências externas
            String[] parts = json.split("\"leituras\":\\[");
            if (parts.length < 2) {
                throw new IllegalArgumentException("JSON inválido: sem campo leituras");
            }
            
            // Extrair timestamp
            String header = parts[0];
            int tsStart = header.indexOf("\"timestamp\":") + 12;
            int tsEnd = header.indexOf(",", tsStart);
            msg.timestamp = Long.parseLong(header.substring(tsStart, tsEnd).trim());
            
            // Extrair leituras
            String leiturasStr = parts[1].substring(0, parts[1].lastIndexOf("]"));
            if (!leiturasStr.trim().isEmpty()) {
                String[] leituraArray = leiturasStr.split("\\},\\{");
                
                for (String leituraStr : leituraArray) {
                    leituraStr = leituraStr.replace("{", "").replace("}", "");
                    LeituraInfo leitura = new LeituraInfo();
                    
                    for (String field : leituraStr.split(",")) {
                        String[] kv = field.split(":", 2);
                        if (kv.length < 2) continue;
                        
                        String key = kv[0].replace("\"", "").trim();
                        String value = kv[1].replace("\"", "").trim();
                        
                        switch (key) {
                            case "sensorId":
                                leitura.sensorId = value;
                                break;
                            case "localizacao":
                                leitura.localizacao = value;
                                break;
                            case "timestamp":
                                leitura.timestamp = Long.parseLong(value);
                                break;
                            case "temperatura":
                                leitura.temperatura = Double.parseDouble(value);
                                break;
                            case "co2":
                                leitura.co2 = Double.parseDouble(value);
                                break;
                            case "umidade":
                                leitura.umidade = Double.parseDouble(value);
                                break;
                            case "ruido":
                                leitura.ruido = Double.parseDouble(value);
                                break;
                            case "radiacao_uv":
                                leitura.radiacao_uv = Double.parseDouble(value);
                                break;
                            case "pm25":
                                leitura.pm25 = Double.parseDouble(value);
                                break;
                            case "pm10":
                                leitura.pm10 = Double.parseDouble(value);
                                break;
                        }
                    }
                    
                    msg.leituras.add(leitura);
                }
            }
            
            return msg;
            
        } catch (Exception e) {
            System.err.println("[TcpDataMessage] Erro ao deserializar: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public byte[] encrypt(SessionKeys keys) throws Exception {
        byte[] plaintext = serialize();
        return CryptoProtocol.encryptAES_HMAC(plaintext, keys);
    }

    @Override
    public Message decrypt(byte[] encryptedData, SessionKeys keys) throws Exception {
        byte[] plaintext = CryptoProtocol.decryptAES_HMAC(encryptedData, keys);
        String json = new String(plaintext, StandardCharsets.UTF_8);
        return deserializeFromString(json);
    }

    public static TcpDataMessage decryptMessage(byte[] encryptedData, SessionKeys keys) throws Exception {
        byte[] plaintext = CryptoProtocol.decryptAES_HMAC(encryptedData, keys);
        String json = new String(plaintext, StandardCharsets.UTF_8);
        return deserializeFromString(json);
    }
    
    @Override
    public String toJSON() {
        return toJSONString();
    }
    
    @Override
    public void validate() throws IllegalStateException {
        if (type != MessageType.TCP_DATA_BATCH) {
            throw new IllegalStateException("Tipo de mensagem deve ser TCP_DATA_BATCH");
        }
        if (leituras == null) {
            throw new IllegalStateException("Leituras não podem ser nulas");
        }
    }
    
    @Override
    public int getSize() {
        return toJSONString().length();
    }
    
    @Override
    public String toString() {
        return String.format("TcpDataMessage{tipo=%s, timestamp=%d, leituras=%d}",
                type, timestamp, leituras.size());
    }
    
    // ========== MÉTODOS DE COMPATIBILIDADE COM MensagemDatacenter ==========

    public byte[] serializar(SessionKeys keys) throws Exception {
        return encrypt(keys);
    }

    public static TcpDataMessage desserializar(byte[] mensagem, SessionKeys keys) throws Exception {
        return decryptMessage(mensagem, keys);
    }

    public static TcpDataMessage criarLote(List<LeituraInfo> leituras) {
        return new TcpDataMessage(leituras);
    }
}
