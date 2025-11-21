package com.project.security;

public class Message {
    private MessageType type;
    private String serverName;
    private String ipAddress;

    public Message(MessageType type, String serverName, String ipAddress) {
        this.type = type;
        this.serverName = serverName;
        this.ipAddress = ipAddress;

    }

    public MessageType getType() {
        return type;
    }

    public String getServerName() {
        return serverName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String serialize() {
        return type.name() + "||" + serverName + "||" + ipAddress;
    }

    public static Message deserialize(String data) {
        String[] parts = data.split("\\|\\|");

        MessageType type = MessageType.valueOf(parts[0]);
        String serverName = parts.length > 1 ? parts[1] : null;
        String ipAddress = parts.length > 2 ? parts[2] : null;
        // throw new IllegalArgumentException("Invalid message format");

        return new Message(type, serverName, ipAddress);
    }

    public String pack() {
        String normalMessage = this.serialize();

        AES aes = new AES();
        aes.setChave(KeyManager.getAESKey());
        String encryptedMessage = aes.cifrar(normalMessage);

        HMAC hmac = new HMAC();
        hmac.setKey(KeyManager.getHMACKey());
        String hmacValue = hmac.generateHMAC(encryptedMessage);

        return encryptedMessage + "||" + hmacValue;
    }

    public static Message unpack(String packedMessage) {
        String[] parts = packedMessage.split("\\|\\|");
        if (parts.length != 2) { // MENSAGEM CIFRADA + HMAC
            return null;
            // throw new IllegalArgumentException("Invalid packed message format");
        }

        String encryptedMessage = parts[0];
        String receivedHmac = parts[1];

        HMAC hmac = new HMAC();
        hmac.setKey(KeyManager.getHMACKey());
        String computedHmac = hmac.generateHMAC(encryptedMessage);
        
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("--- DEBUG INFO ---");
            System.out.println("DEBUG: Encrypted message: " + encryptedMessage);
            System.out.println("DEBUG: Received HMAC:    " + receivedHmac);
            System.out.println("DEBUG: Computed HMAC:    " + computedHmac);
            System.out.println("DEBUG: HMAC match:       " + computedHmac.equals(receivedHmac));
            System.out.println("-------------------");
            System.out.println("");
        }
        
        boolean isValid = hmac.verifyHMAC(encryptedMessage, receivedHmac);

        if (!isValid) {
            System.out.println("HMAC verification failed. FAKE NEWSSON?");
            return null;

        }

        AES aes = new AES();
        aes.setChave(KeyManager.getAESKey());
        String normalMessage = aes.decifrar(encryptedMessage);

        if (normalMessage == null) {
            System.out.println("Decryption failed. FAKE NEWSSON? Probably silently caught exception.");
            return null;
        }

        return Message.deserialize(normalMessage);
    }

}
