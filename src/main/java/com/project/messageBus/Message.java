package com.project.messageBus;

import java.io.Serializable;

public abstract class Message implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    protected MessageType type;
    protected long timestamp;

    public Message(MessageType type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public Message(MessageType type, long timestamp) {
        this.type = type;
        this.timestamp = timestamp;
    }
    
    // Getters
    public MessageType getType() {
        return type;
    }
    
    public long getTimestamp() {
        return timestamp;
    }

    public abstract byte[] serialize() throws Exception;

    public abstract String toJSON();

    public abstract void validate() throws IllegalStateException;

    public abstract int getSize();
    
    @Override
    public String toString() {
        return String.format("%s{type=%s, timestamp=%d}", 
                getClass().getSimpleName(), type, timestamp);
    }
}
