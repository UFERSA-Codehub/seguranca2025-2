package com.project.client;

public interface IClient {

    boolean autenticar(String usuario, String senha);

    boolean descobrirDatacenter();

    String consultarIQA(long inicio, long fim) throws Exception;

    String consultarTendencias(long inicio, long fim) throws Exception;

    String consultarMicroclima(String localizacao, long inicio, long fim) throws Exception;

    String consultarEnchentes(String localizacao) throws Exception;

    String consultarTrafego(String localizacao, long inicio, long fim) throws Exception;

    String consultarStatus() throws Exception;

    boolean isAutenticado();

    String getDatacenterInfo();
}
