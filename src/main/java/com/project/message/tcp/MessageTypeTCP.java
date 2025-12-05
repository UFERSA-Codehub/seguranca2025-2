package com.project.message.tcp;

public enum MessageTypeTCP {
    // Handshake
    HELLO,                  // Primeiro contato, inclui a chave pública do emissor
    CHALLENGE,              // Resposta ao HELLO, inclui a chave pública do receptor + chaves da sessão cifradas

    // Autenticação Edge → Datacenter (shared secret)
    EDGE_AUTH,              // Edge envia credenciais (edgeId + secret) para autenticar no Datacenter
    EDGE_AUTH_OK,           // Datacenter confirma autenticação do Edge
    EDGE_AUTH_FAIL,         // Datacenter rejeita autenticação do Edge

    DATA_BATCH,             // Lote de dados dos sensores enviados pelo Edge para o DataCenter
    DATA_ACK,               // Confirmação de recebimento do lote de dados

    AUTH,                   // Mensagem de autenticação (Cliente -> Datacenter) com JWT Token
    AUTH_OK,                // Resposta de autenticação bem-sucedida
    AUTH_FAIL,              // Resposta de falha na autenticação

    QUERY_DATA,             // Request para consulta de dados armazenados
    QUERY_REPORT,           // Request para geração de relatório
    QUERY_RESPONSE,         // Resposta com os dados consultados
    
    ERROR
}
