package com.project.message.http;

public enum MessageTypeHTTP {
    // Handshake
    HELLO,
    CHALLENGE,
    
    // Autenticação
    AUTH,
    AUTH_OK,
    AUTH_FAIL,
    
    // Consultas
    QUERY_DATA,
    QUERY_REPORT,
    QUERY_ALERTS,
    QUERY_STATUS,
    
    // Respostas
    RESPONSE,
    
    // Erros
    ERROR
}
