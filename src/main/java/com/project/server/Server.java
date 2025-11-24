package com.project.server;

/**
 * Interface base para todos os servidores do sistema.
 * Define o contrato comum para inicialização, parada e monitoramento.
 */
public interface Server {
    
    /**
     * Inicia o servidor e seus componentes.
     * @throws Exception se houver erro na inicialização
     */
    void iniciar() throws Exception;
    
    /**
     * Para o servidor de forma graceful.
     */
    void parar();
    
    /**
     * Verifica se o servidor está em execução.
     * @return true se executando, false caso contrário
     */
    boolean isExecutando();
    
    /**
     * Exibe estatísticas/status do servidor.
     */
    void exibirStatus();
    
    /**
     * Retorna o nome/identificador do servidor.
     * @return nome do servidor
     */
    String getNome();
    
    /**
     * Retorna a porta principal do servidor.
     * @return porta ou -1 se não aplicável
     */
    int getPorta();
}
