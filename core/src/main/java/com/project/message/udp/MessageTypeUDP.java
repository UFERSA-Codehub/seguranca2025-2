package com.project.message.udp;

public enum MessageTypeUDP {

    // Handshake
    HELLO,                  // Primeiro contato, inclui a chave pública do emissor
    CHALLENGE,              // Resposta ao HELLO, inclui a chave pública do receptor + chaves da sessão cifradas

    LOOK_EDGE,              // Request para descobrir um Edge próximo
    FOUND_EDGE,             // Resposta com informações do Edge próximo

    AUTH,                   // Mensagem de autenticação (Sensor -> Edge) com JWT Token
    AUTH_OK,                // Resposta de autenticação bem-sucedida
    AUTH_FAIL,              // Resposta de falha na autenticação
    
    LOOK_DATACENTER,        // Request para descobrir um DataCenter próximo
    FOUND_DATACENTER,       // Resposta com informações do DataCenter próximo

    DATA,                   // Pacote de dados cifrados entre Sensor -> Edge (cifrado, assinado e com token JWT)
    NOT_FOUND,              // Resposta indicando que o recurso/serviço não foi encontrado

    REGISTER_EDGE,          // Registro de um Edge no Discovery
    REGISTER_DATACENTER,    // Registro de um DataCenter no Discovery
    REGISTER_AUTH,          // Registro de um AuthServer no Discovery
    REGISTER_OK,            // Resposta de registro bem-sucedido
    REGISTER_FAIL,          // Resposta de falha no registro

    HEARTBEAT,              // Heartbeat periódico (Edge/Datacenter -> Discovery)
    HEARTBEAT_OK,           // Resposta de heartbeat bem-sucedido
    RE_REGISTER;            // Sinal do Discovery para serviço re-registrar (após restart)

}
