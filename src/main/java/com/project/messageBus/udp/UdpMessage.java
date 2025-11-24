package com.project.messageBus.udp;

import com.project.messageBus.Message;
import com.project.messageBus.MessageType;
import com.project.messageBus.SecureMessage;
import com.project.model.DadosAmbientais;
import com.project.security.SessionKeys;
import com.project.security.CryptoProtocol;
import java.nio.charset.StandardCharsets;

public class UdpMessage extends Message implements SecureMessage {
    
    private static final long serialVersionUID = 1L;
    
    private String sensorId;
    private String credenciais;
    private DadosAmbientais dados;
    private String publicKeyBase64;
    private byte[] encryptedSessionKeys;

    public UdpMessage(MessageType tipo, String sensorId, String credenciais, DadosAmbientais dados) {
        super(tipo);
        
        if (!tipo.isUDP() && !tipo.isSensorAuth()) {
            throw new IllegalArgumentException("Tipo deve ser SENSOR_REGISTER, SENSOR_UPDATE ou tipo de autenticação");
        }
        
        this.sensorId = sensorId;
        this.credenciais = credenciais;
        this.dados = dados;  // Pode ser null para mensagens de autenticação
    }

    public UdpMessage(MessageType tipo, String sensorId, String credenciais, 
                      DadosAmbientais dados, long timestamp) {
        super(tipo, timestamp);
        
        if (!tipo.isUDP() && !tipo.isSensorAuth()) {
            throw new IllegalArgumentException("Tipo deve ser SENSOR_REGISTER, SENSOR_UPDATE ou tipo de autenticação");
        }
        
        this.sensorId = sensorId;
        this.credenciais = credenciais;
        this.dados = dados;  // Pode ser null para mensagens de autenticação
    }
    
    // Getters
    public String getSensorId() {
        return sensorId;
    }
    
    public String getCredenciais() {
        return credenciais;
    }
    
    public String getToken() {
        return credenciais;  // Token é transmitido no campo credenciais
    }
    
    public DadosAmbientais getDados() {
        return dados;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public byte[] getEncryptedSessionKeys() {
        return encryptedSessionKeys;
    }

    public static UdpMessage createHello(String sensorId, String senha) {
        UdpMessage msg = new UdpMessage(MessageType.SENSOR_HELLO, sensorId, senha, null);
        return msg;
    }

    public static UdpMessage createChallenge(String edgeId, String publicKeyBase64) {
        UdpMessage msg = new UdpMessage(MessageType.SENSOR_CHALLENGE, edgeId, null, null);
        msg.publicKeyBase64 = publicKeyBase64;
        return msg;
    }

    public static UdpMessage createKeyExchange(String sensorId, byte[] encryptedSessionKeys) {
        UdpMessage msg = new UdpMessage(MessageType.SENSOR_KEY_EXCHANGE, sensorId, null, null);
        msg.encryptedSessionKeys = encryptedSessionKeys;
        return msg;
    }

    public String serializeToString() {
        if (type == MessageType.SENSOR_HELLO) {
            return String.format("%s||%s||%s||%d",
                type.name(),
                sensorId,
                credenciais != null ? credenciais : "",
                timestamp
            );
        }

        if (type == MessageType.SENSOR_CHALLENGE) {
            return String.format("%s||%s||%s||%d",
                type.name(),
                sensorId,
                publicKeyBase64 != null ? publicKeyBase64 : "",
                timestamp
            );
        }

        if (type == MessageType.SENSOR_KEY_EXCHANGE) {
            String encodedKeys = encryptedSessionKeys != null ?
                java.util.Base64.getEncoder().encodeToString(encryptedSessionKeys) : "";
            return String.format("%s||%s||%s||%d",
                type.name(),
                sensorId,
                encodedKeys,
                timestamp
            );
        }

        if (dados == null) {
            return String.format("%s||%s||%s||%d",
                type.name(),
                sensorId,
                credenciais != null ? credenciais : "",
                timestamp
            );
        }
        
        // Formato normal com dados ambientais
        return String.format("%s||%s||%s||%d||%s||%.2f||%.2f||%.2f||%.2f||%.2f||%.2f||%.2f",
            type.name(),
            sensorId,
            credenciais,
            timestamp,
            dados.getLocalizacao(),
            dados.getTemperatura(),
            dados.getCo2(),
            dados.getUmidade(),
            dados.getRuido(),
            dados.getRadiacao_uv(),
            dados.getPm25(),
            dados.getPm10()
        );
    }

    @Override
    public byte[] serialize() {
        return serializeToString().getBytes(StandardCharsets.UTF_8);
    }

    public static UdpMessage deserializeFromString(String data) {
        try {
            String[] parts = data.split("\\|\\|");
            
            if (parts.length < 4) {
                throw new IllegalArgumentException("Formato de mensagem inválido: campos mínimos insuficientes");
            }
            
            MessageType tipo = MessageType.valueOf(parts[0]);
            String sensorId = parts[1];
            String campo2 = parts[2];
            long timestamp = Long.parseLong(parts[3]);

            if (tipo == MessageType.SENSOR_HELLO) {
                UdpMessage msg = createHello(sensorId, campo2);
                msg.timestamp = timestamp;
                return msg;
            }

            if (tipo == MessageType.SENSOR_CHALLENGE) {
                UdpMessage msg = createChallenge(sensorId, campo2);
                msg.timestamp = timestamp;
                return msg;
            }

            if (tipo == MessageType.SENSOR_KEY_EXCHANGE) {
                byte[] encrypted = java.util.Base64.getDecoder().decode(campo2);
                UdpMessage msg = createKeyExchange(sensorId, encrypted);
                msg.timestamp = timestamp;
                return msg;
            }

            if (tipo.isSensorAuth()) {
                return new UdpMessage(tipo, sensorId, campo2, null, timestamp);
            }
            
            // Para mensagens com dados ambientais
            if (parts.length < 12) {
                throw new IllegalArgumentException("Formato de mensagem inválido: campos insuficientes para dados ambientais");
            }
            
            String localizacao = parts[4];
            double temperatura = Double.parseDouble(parts[5]);
            double co2 = Double.parseDouble(parts[6]);
            double umidade = Double.parseDouble(parts[7]);
            double ruido = Double.parseDouble(parts[8]);
            double radiacao_uv = Double.parseDouble(parts[9]);
            double pm25 = Double.parseDouble(parts[10]);
            double pm10 = Double.parseDouble(parts[11]);
            
            DadosAmbientais dados = new DadosAmbientais(
                timestamp, localizacao, temperatura, co2,
                umidade, ruido, radiacao_uv, pm25, pm10
            );
            
            return new UdpMessage(tipo, sensorId, campo2, dados, timestamp);
            
        } catch (Exception e) {
            //System.err.println("[UdpMessage] Erro ao deserializar: " + e.getMessage());
            //e.printStackTrace();
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
        String data = new String(plaintext, StandardCharsets.UTF_8);
        return deserializeFromString(data);
    }

    public static UdpMessage decryptMessage(byte[] encryptedData, SessionKeys keys) throws Exception {
        byte[] plaintext = CryptoProtocol.decryptAES_HMAC(encryptedData, keys);
        String data = new String(plaintext, StandardCharsets.UTF_8);
        return deserializeFromString(data);
    }
    
    @Override
    public String toJSON() {
        if (dados == null) {
            return String.format(
                "{\"type\":\"%s\",\"sensorId\":\"%s\",\"timestamp\":%d}",
                type.name(), sensorId, timestamp
            );
        }
        return String.format(
            "{\"type\":\"%s\",\"sensorId\":\"%s\",\"timestamp\":%d,\"dados\":{\"temp\":%.2f,\"co2\":%.2f,\"umid\":%.2f}}",
            type.name(), sensorId, timestamp,
            dados.getTemperatura(), dados.getCo2(), dados.getUmidade()
        );
    }
    
    @Override
    public void validate() throws IllegalStateException {
        if (sensorId == null || sensorId.isEmpty()) {
            throw new IllegalStateException("SensorId não pode ser nulo ou vazio");
        }
        if (credenciais == null || credenciais.isEmpty()) {
            throw new IllegalStateException("Credenciais não podem ser nulas ou vazias");
        }
        // Dados podem ser null para mensagens de autenticação
        if (dados == null && !type.isSensorAuth()) {
            throw new IllegalStateException("Dados ambientais não podem ser nulos para mensagens de dados");
        }
        if (!type.isUDP() && !type.isSensorAuth()) {
            throw new IllegalStateException("Tipo de mensagem inválido para UdpMessage");
        }
    }
    
    @Override
    public int getSize() {
        return serializeToString().length();
    }
    
    @Override
    public String toString() {
        return String.format("UdpMessage{tipo=%s, sensorId='%s', timestamp=%d, dados=%s}",
            type, sensorId, timestamp, dados);
    }
}
