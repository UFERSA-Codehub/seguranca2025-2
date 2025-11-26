package com.project.message.udp;

public enum MessageTypeUDP {

    /**
     *  UDP Handshake 
     * Sensor       <->     Discovery
     * Edge         <->     Discovery 
     * DataCenter   <->     Discovery 
     * User         <->     Discovery
     * Sensor       <->     Edge
     */
    HELLO,
    CHALLENGE,
    KEY_EXCHANGE,
    ACK,

    LOOK_EDGE,
    FOUND_EDGE,
    
    LOOK_DATACENTER,
    FOUND_DATACENTER,

    DATA,
    NOT_FOUND
}
