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

    AUTH,                   // Mensagem de autenticação (Sensor -> AuthServer) com credenciais
    AUTH_OK,                // Resposta de autenticação bem-sucedida com JWT
    AUTH_FAIL,              // Resposta de falha na autenticação

    VALIDATE,               // Datacenter -> AuthServer: validar credenciais de usuário
    VALIDATE_OK,            // AuthServer -> Datacenter: credenciais válidas, inclui JWT
    VALIDATE_FAIL,          // AuthServer -> Datacenter: credenciais inválidas

    DATA,                   // Sensor -> Edge: envio de dados com JWT
    DATA_OK,                // Edge -> Sensor: confirmação de recebimento

    QUERY_DATA,             // Request para consulta de dados armazenados
    QUERY_REPORT,           // Request para geração de relatório
    QUERY_RESPONSE,         // Resposta com os dados consultados

    // IDS e Firewall
    ALERT,                  // Firewall → IDS: atividade suspeita detectada
    ALERT_ACK,              // IDS → Firewall: alerta recebido
    TERMINATE,              // IDS → Edge: encerrar conexão por IP
    TERMINATE_ACK,          // Edge → IDS: conexão encerrada
    
    ERROR
}
