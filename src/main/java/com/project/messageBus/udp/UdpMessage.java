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

    public UdpMessage(MessageType tipo, String sensorId, String credenciais, DadosAmbientais dados) {
        super(tipo);
        
        if (!tipo.isUDP()) {
            throw new IllegalArgumentException("Tipo deve ser SENSOR_REGISTER ou SENSOR_UPDATE");
        }
        
        this.sensorId = sensorId;
        this.credenciais = credenciais;
        this.dados = dados;
    }

    public UdpMessage(MessageType tipo, String sensorId, String credenciais, 
                      DadosAmbientais dados, long timestamp) {
        super(tipo, timestamp);
        
        if (!tipo.isUDP()) {
            throw new IllegalArgumentException("Tipo deve ser SENSOR_REGISTER ou SENSOR_UPDATE");
        }
        
        this.sensorId = sensorId;
        this.credenciais = credenciais;
        this.dados = dados;
    }
    
    // Getters
    public String getSensorId() {
        return sensorId;
    }
    
    public String getCredenciais() {
        return credenciais;
    }
    
    public DadosAmbientais getDados() {
        return dados;
    }

    private String serializeToString() {
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
            
            if (parts.length < 12) {
                throw new IllegalArgumentException("Formato de mensagem inválido: campos insuficientes");
            }
            
            MessageType tipo = MessageType.valueOf(parts[0]);
            String sensorId = parts[1];
            String credenciais = parts[2];
            long timestamp = Long.parseLong(parts[3]);
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
            
            return new UdpMessage(tipo, sensorId, credenciais, dados, timestamp);
            
        } catch (Exception e) {
            System.err.println("[UdpMessage] Erro ao deserializar: " + e.getMessage());
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
        if (dados == null) {
            throw new IllegalStateException("Dados ambientais não podem ser nulos");
        }
        if (!type.isUDP()) {
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
    
    // ========== MÉTODOS DE COMPATIBILIDADE COM MensagemSensor ==========

    public String serializar() {
        return serializeToString();
    }

    public static UdpMessage deserializar(String data) {
        return deserializeFromString(data);
    }

    public String empacotar(SessionKeys keys) throws Exception {
        byte[] encrypted = encrypt(keys);
        
        // Para compatibilidade, extrair HMAC e CIPHERTEXT
        // Formato CryptoProtocol: [IV(16)][HMAC(32)][CIPHERTEXT]
        // Formato antigo: HMAC||CIPHERTEXT
        
        byte[] hmac = new byte[32];
        byte[] ivAndCipher = new byte[encrypted.length - 32];
        
        System.arraycopy(encrypted, 16, hmac, 0, 32); // HMAC está em [16..48)
        System.arraycopy(encrypted, 0, ivAndCipher, 0, 16); // IV
        System.arraycopy(encrypted, 48, ivAndCipher, 16, encrypted.length - 48); // CIPHER
        
        String hmacHex = bytesToHex(hmac);
        String cipherBase64 = java.util.Base64.getEncoder().encodeToString(ivAndCipher);
        
        return hmacHex + "||" + cipherBase64;
    }

    public static UdpMessage desempacotar(String pacote, SessionKeys keys) throws Exception {
        // Converter formato antigo HMAC||CIPHERTEXT para novo formato
        String[] parts = pacote.split("\\|\\|", 2);
        
        if (parts.length < 2) {
            throw new IllegalArgumentException("Formato de pacote inválido");
        }
        
        byte[] hmac = hexToBytes(parts[0]);
        byte[] ivAndCipher = java.util.Base64.getDecoder().decode(parts[1]);
        
        // Reconstruir formato: [IV(16)][HMAC(32)][CIPHERTEXT]
        byte[] encrypted = new byte[ivAndCipher.length + 32];
        System.arraycopy(ivAndCipher, 0, encrypted, 0, 16); // IV
        System.arraycopy(hmac, 0, encrypted, 16, 32); // HMAC
        System.arraycopy(ivAndCipher, 16, encrypted, 48, ivAndCipher.length - 16); // CIPHER
        
        return decryptMessage(encrypted, keys);
    }
    
    // Métodos auxiliares para conversão hex
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
