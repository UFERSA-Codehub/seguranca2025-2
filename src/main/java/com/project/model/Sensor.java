package com.project.model;

import com.project.security.DebugConfig;

public class Sensor {
    private String id;                          // Identificador único do sensor (ex: "SENSOR_001")
    private String nome;                        // Nome amigável do sensor (ex: "Sensor Central Park")
    private String localizacao;                 // Localização física do sensor (ex: "Av. Principal, 123")
    private boolean ativo;                      // Indica se o sensor está ativo ou inativo
    private DadosAmbientais ultimaLeitura;      // Última leitura de dados ambientais coletada pelo sensor
    private String credenciais;                 // Token para autenticação

    public Sensor(String id, String nome, String localizacao, String credenciais) {
        this.id = id;
        this.nome = nome;
        this.localizacao = localizacao;
        this.ativo = true;                      // Sensores começam ativos
        this.ultimaLeitura = null;              // Inicialmente sem leitura
        this.credenciais = credenciais;
    }

    // Getters
    public String getId() {
        return id;
    }
    public String getNome() {
        return nome;
    }
    public String getLocalizacao() {
        return localizacao;
    }
    public boolean isAtivo() {
        return ativo;
    }
    public DadosAmbientais getUltimaLeitura() {
        return ultimaLeitura;
    }
    public String getCredenciais() {
        return credenciais;
    }

    public void setNome(String nome) {
        this.nome = nome;

        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[Sensor]: Nome do sensor " + this.id + " atualizado para: " + nome);
        }
    }

    public void setLocalizacao(String localizacao) {
        this.localizacao = localizacao;

        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[Sensor]: Localização do sensor " + this.id + " atualizada para: " + localizacao);
        }
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;

        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[Sensor]: Status do sensor " + this.id + " atualizado para: " + (ativo ? "Ativo" : "Inativo"));
        }
    }

    //Lógica de negócio
    public boolean atualizarLeitura(DadosAmbientais novaLeitura) {
        if (novaLeitura == null){
            return false;
        }

        if (!this.localizacao.equals(novaLeitura.getLocalizacao())) {
            System.out.println("[Sensor]: ERRO: Localização da leitura não corresponde ao sensor!");
            return false;
        }

        this.ultimaLeitura = novaLeitura;
        
        if (DebugConfig.DEBUG_MODE) {
            System.out.println("[Sensor]: Leitura atualizada para o sensor " + this.id);
        }
        
        return true;

    }

    public boolean isConectado() {
        if (!ativo) {
            return false;
        }

        if (ultimaLeitura == null) {
            return false;
        }

        long agora = System.currentTimeMillis();
        long diferenca = agora - ultimaLeitura.getTimestamp();
        long TIMEOUT = 60 * 1000; // 1 minuto

        return diferenca <= TIMEOUT;
    }

    @Override
    public String toString() {
        return String.format("Sensor{id='%s', nome='%s', localizacao='%s', ativo=%b, conectado=%s, ultimaLeitura=%s}",
                id, 
                nome, 
                localizacao,
                ativo, 
                isConectado(), 
                (ultimaLeitura != null ? "existente" : "null")
        );
    }
}