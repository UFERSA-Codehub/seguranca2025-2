package com.project.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.discovery.DiscoveryClient;
import com.project.discovery.ServiceInfo;
import com.project.client.config.ClientConfig;
import com.project.client.util.RetryHelper;
import com.project.client.util.TokenCache;

public class ClientImpl implements IClient {

    private static final Logger logger = LoggerFactory.getLogger(ClientImpl.class);

    private String datacenterHost;
    private int datacenterPorta;
    private String token;
    private ClienteHTTP clienteHTTP;
    private DiscoveryClient clienteDiscovery;
    private ClientConfig config;
    private RetryHelper retryHelper;
    private TokenCache tokenCache;

    public ClientImpl(ClientConfig config) {
        this.config = config;
        this.clienteDiscovery = new DiscoveryClient(
            config.getDiscoveryHost(),
            config.getDiscoveryPort()
        );

        if (config.isRetryEnabled()) {
            this.retryHelper = new RetryHelper(
                config.getRetryMaxAttempts(),
                config.getRetryInitialDelayMs(),
                config.isRetryExponentialBackoff()
            );
            logger.info("Retry habilitado: {} tentativas, {}ms delay inicial{}",
                config.getRetryMaxAttempts(),
                config.getRetryInitialDelayMs(),
                config.isRetryExponentialBackoff() ? " (exponencial)" : "");
        }

        if (config.isCacheEnabled()) {
            this.tokenCache = new TokenCache(
                config.getCacheDirectory(),
                config.getCacheTokenTtlHours()
            );
            logger.info("Cache habilitado: TTL={}h, dir={}",
                config.getCacheTokenTtlHours(),
                config.getCacheDirectory());
        }

    }
    
    @Override
    public boolean descobrirDatacenter() {
        try {
            logger.info("Descobrindo Datacenter via Discovery Service...");

            ServiceInfo info = clienteDiscovery.descobrirDatacenter();

            if (info != null) {
                this.datacenterHost = info.getHost();
                this.datacenterPorta = info.getPorta() + 1010;

                logger.info("Datacenter descoberto: host={}, portaTCP={}, portaHTTP={}",
                    datacenterHost, info.getPorta(), datacenterPorta);

                return true;
            } else {
                logger.error("Datacenter não encontrado no Discovery Service");
                return false;
            }

        } catch (Exception e) {
            logger.error("Erro ao descobrir Datacenter: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean autenticar(String usuario, String senha) {
        try {
            // Verificar se já descobriu o Datacenter
            if (datacenterHost == null) {
                logger.warn("Datacenter não descoberto. Execute descobrirDatacenter() primeiro.");
                return false;
            }

            // Verificar cache primeiro (se habilitado)
            if (tokenCache != null) {
                String cachedToken = tokenCache.carregar();
                if (cachedToken != null) {
                    this.token = cachedToken;
                    this.clienteHTTP = new ClienteHTTP(getProtocol(), datacenterHost, datacenterPorta, token,
                                                       config.getHttpConnectTimeout(), config.getHttpReadTimeout());
                    logger.info("Autenticado usando token em cache");
                    return true;
                }
            }

            logger.info("Autenticando como: {}", usuario);

            // Autenticar com retry (se habilitado)
            if (retryHelper != null) {
                this.token = retryHelper.execute(
                    () -> {
                        try {
                            return ClienteHTTP.autenticar(
                                getProtocol(),
                                datacenterHost,
                                datacenterPorta,
                                usuario,
                                senha,
                                config.getHttpConnectTimeout(),
                                config.getHttpReadTimeout()
                            );
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    },
                    "Autenticação JWT"
                );
            } else {
                this.token = ClienteHTTP.autenticar(
                    getProtocol(),
                    datacenterHost,
                    datacenterPorta,
                    usuario,
                    senha,
                    config.getHttpConnectTimeout(),
                    config.getHttpReadTimeout()
                );
            }

            if (token != null && !token.isEmpty()) {
                // Criar cliente HTTP com token
                this.clienteHTTP = new ClienteHTTP(getProtocol(), datacenterHost, datacenterPorta, token,
                                                   config.getHttpConnectTimeout(), config.getHttpReadTimeout());

                // Salvar token em cache (se habilitado)
                if (tokenCache != null) {
                    tokenCache.salvar(token);
                }

                logger.info("Autenticação bem-sucedida, token: {}...",
                    token.substring(0, Math.min(20, token.length())));

                return true;
            } else {
                logger.error("Falha na autenticação: token vazio");
                return false;
            }

        } catch (Exception e) {
            logger.error("Erro na autenticação: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String consultarIQA(long inicio, long fim) throws Exception {
        validarClienteHTTP();

        if (retryHelper != null) {
            return retryHelper.execute(
                () -> {
                    try {
                        return clienteHTTP.consultarIQA(inicio, fim);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                "Consulta IQA"
            );
        }

        return clienteHTTP.consultarIQA(inicio, fim);
    }
    
    @Override
    public String consultarTendencias(long inicio, long fim) throws Exception {
        validarClienteHTTP();

        if (retryHelper != null) {
            return retryHelper.execute(
                () -> {
                    try {
                        return clienteHTTP.consultarTendencias(inicio, fim);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                "Consulta Tendências"
            );
        }

        return clienteHTTP.consultarTendencias(inicio, fim);
    }
    
    @Override
    public String consultarMicroclima(String localizacao, long inicio, long fim) throws Exception {
        validarClienteHTTP();

        if (retryHelper != null) {
            return retryHelper.execute(
                () -> {
                    try {
                        return clienteHTTP.consultarMicroclima(localizacao, inicio, fim);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                "Consulta Microclima"
            );
        }

        return clienteHTTP.consultarMicroclima(localizacao, inicio, fim);
    }
    
    @Override
    public String consultarEnchentes(String localizacao) throws Exception {
        validarClienteHTTP();

        if (retryHelper != null) {
            return retryHelper.execute(
                () -> {
                    try {
                        return clienteHTTP.consultarEnchentes(localizacao);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                "Consulta Enchentes"
            );
        }

        return clienteHTTP.consultarEnchentes(localizacao);
    }
    
    @Override
    public String consultarTrafego(String localizacao, long inicio, long fim) throws Exception {
        validarClienteHTTP();

        if (retryHelper != null) {
            return retryHelper.execute(
                () -> {
                    try {
                        return clienteHTTP.consultarTrafego(localizacao, inicio, fim);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                "Consulta Tráfego"
            );
        }

        return clienteHTTP.consultarTrafego(localizacao, inicio, fim);
    }
    
    @Override
    public String consultarStatus() throws Exception {
        validarClienteHTTP();

        if (retryHelper != null) {
            return retryHelper.execute(
                () -> {
                    try {
                        return clienteHTTP.consultarStatus();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                "Consulta Status"
            );
        }

        return clienteHTTP.consultarStatus();
    }
    
    @Override
    public boolean isAutenticado() {
        return token != null && !token.isEmpty() && clienteHTTP != null;
    }
    
    @Override
    public String getDatacenterInfo() {
        if (datacenterHost == null) {
            return "Não conectado";
        }
        return datacenterHost + ":" + datacenterPorta;
    }

    private void validarClienteHTTP() throws Exception {
        if (clienteHTTP == null) {
            throw new Exception("Cliente não autenticado. Execute autenticar() primeiro.");
        }
    }

    private String getProtocol() {
        return config.isHttpsEnabled() ? "https" : "http";
    }

    public void inspecionarToken() {
        if (token != null) {
            ClienteHTTP.inspecionarToken(token);
        } else {
            logger.warn("Nenhum token disponível");
        }
    }
    
    // Getters
    public void limparCache() {
        if (tokenCache != null) {
            tokenCache.limpar();
        } else {
            logger.info("Cache não habilitado");
        }
    }
}
