package com.project.messageBus;

public enum MessageType {
    
    // ========== UDP (Sensor → Edge) ==========
    
    SENSOR_REGISTER,

    SENSOR_UPDATE,
    
    SENSOR_AUTH_REQUEST,
    
    SENSOR_AUTH_SUCCESS,
    
    SENSOR_AUTH_FAILED,

    SENSOR_HELLO,

    SENSOR_CHALLENGE,

    SENSOR_KEY_EXCHANGE,

    // ========== TCP Handshake (Edge ↔ Datacenter) ==========
    
    TCP_HELLO,

    TCP_CHALLENGE,

    TCP_KEY_EXCHANGE,

    TCP_ACK,

    TCP_ERROR,
    
    // ========== TCP Data (Edge → Datacenter) ==========
    
    TCP_DATA_BATCH,
    
    // ========== HTTP (Futuro - Datacenter ↔ Clientes) ==========
    
    HTTP_REQUEST,

    HTTP_RESPONSE,
    
    // ========== Discovery (Serviços ↔ Servidor Localização) ==========
    
    DISCOVERY_REGISTER_EDGE,

    DISCOVERY_REGISTER_DATACENTER,

    DISCOVERY_FIND_EDGE,

    DISCOVERY_FIND_DATACENTER,

    DISCOVERY_RESPONSE_EDGE,

    DISCOVERY_RESPONSE_DATACENTER,

    DISCOVERY_HEARTBEAT,

    DISCOVERY_ERROR;

    public boolean isUDP() {
        return this == SENSOR_REGISTER || this == SENSOR_UPDATE;
    }
    
    public boolean isSensorAuth() {
        return this == SENSOR_AUTH_REQUEST ||
               this == SENSOR_AUTH_SUCCESS ||
               this == SENSOR_AUTH_FAILED ||
               this == SENSOR_HELLO ||
               this == SENSOR_CHALLENGE ||
               this == SENSOR_KEY_EXCHANGE;
    }

    public boolean isTcpHandshake() {
        return this == TCP_HELLO || 
               this == TCP_CHALLENGE || 
               this == TCP_KEY_EXCHANGE || 
               this == TCP_ACK || 
               this == TCP_ERROR;
    }

    public boolean isTcpData() {
        return this == TCP_DATA_BATCH;
    }

    public boolean isHTTP() {
        return this == HTTP_REQUEST || this == HTTP_RESPONSE;
    }

    public boolean isDiscovery() {
        return this == DISCOVERY_REGISTER_EDGE ||
               this == DISCOVERY_REGISTER_DATACENTER ||
               this == DISCOVERY_FIND_EDGE ||
               this == DISCOVERY_FIND_DATACENTER ||
               this == DISCOVERY_RESPONSE_EDGE ||
               this == DISCOVERY_RESPONSE_DATACENTER ||
               this == DISCOVERY_HEARTBEAT ||
               this == DISCOVERY_ERROR;
    }
}
