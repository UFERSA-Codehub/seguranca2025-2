package com.project.edge;

import com.project.security.JWTManager;
import com.project.security.RateLimiter;
import java.util.HashMap;
import java.util.Map;

public class GestorAutenticacao {
    private Map<String, String> senhasSensores;       // sensorId -> senha (plaintext por simplicidade)
    private Map<String, String> tokensAtivos;         // sensorId -> JWT ativo
    private RateLimiter rateLimiter;                  // Controle de rate limiting

    public GestorAutenticacao() {
        this.senhasSensores = new HashMap<>();
        this.tokensAtivos = new HashMap<>();
        this.rateLimiter = new RateLimiter(1, 60000);  // 1 tentativa, 1 minuto de bloqueio
        inicializarSenhas();
    }

    private void inicializarSenhas() {
        // Em produção, usar BCrypt e banco de dados
        senhasSensores.put("SENSOR_001", "senha123");
        senhasSensores.put("SENSOR_002", "industrial456");
        senhasSensores.put("SENSOR_003", "comercial789");
        senhasSensores.put("SENSOR_004", "residencial321");
    }

    public String registrarESensorEObterJWT(String sensorId, String senha) {
        if (sensorId == null || senha == null) {
            System.err.println("[GestorAutenticacao] SensorId ou senha nulos");
            return null;
        }

        if (rateLimiter.estaBloqueado(sensorId)) {
            long tempoRestante = rateLimiter.getTempoRestanteBloqueio(sensorId);
            System.err.println("[GestorAutenticacao] Sensor " + sensorId + " está temporariamente bloqueado (" + tempoRestante + "s restantes)");
            return null;
        }

        if (!validarCredenciais(sensorId, senha)) {
            System.err.println("[GestorAutenticacao] ❌ Credenciais inválidas para sensor: " + sensorId);
            rateLimiter.registrarTentativaFalha(sensorId);
            return null;
        }

        // Gerar JWT
        Map<String, Object> claims = Map.of(
            "tipo", "sensor",
            "sensorId", sensorId
        );
        String jwt = JWTManager.gerarToken(sensorId, claims);
        
        tokensAtivos.put(sensorId, jwt);
        rateLimiter.resetar(sensorId);
        
        System.out.println("[GestorAutenticacao] ✅ JWT gerado para sensor: " + sensorId);
        return jwt;
    }

    public boolean autenticarComJWT(String sensorId, String jwt) {
        if (sensorId == null || jwt == null) {
            System.err.println("[GestorAutenticacao] SensorId ou JWT nulos");
            return false;
        }

        if (rateLimiter.estaBloqueado(sensorId)) {
            long tempoRestante = rateLimiter.getTempoRestanteBloqueio(sensorId);
            System.err.println("[GestorAutenticacao] Sensor " + sensorId + " está temporariamente bloqueado (" + tempoRestante + "s restantes)");
            return false;
        }

        // Validar JWT
        if (!JWTManager.validarToken(jwt)) {
            System.err.println("[GestorAutenticacao] ❌ JWT inválido para sensor: " + sensorId);
            rateLimiter.registrarTentativaFalha(sensorId);
            return false;
        }

        // Verificar se sensorId no JWT corresponde
        String sensorIdNoToken = JWTManager.extrairUsuario(jwt);
        if (!sensorId.equals(sensorIdNoToken)) {
            System.err.println("[GestorAutenticacao] ❌ SensorId no JWT não corresponde: esperado=" + 
                sensorId + ", recebido=" + sensorIdNoToken);
            rateLimiter.registrarTentativaFalha(sensorId);
            return false;
        }

        // Verificar se sensor está autorizado
        if (!senhasSensores.containsKey(sensorId)) {
            System.err.println("[GestorAutenticacao] ❌ Sensor não autorizado: " + sensorId);
            rateLimiter.registrarTentativaFalha(sensorId);
            return false;
        }

        rateLimiter.resetar(sensorId);
        return true;
    }

    private boolean validarCredenciais(String sensorId, String senha) {
        return senhasSensores.containsKey(sensorId) && 
               senhasSensores.get(sensorId).equals(senha);
    }

    public void revogarToken(String sensorId) {
        tokensAtivos.remove(sensorId);
        System.out.println("[GestorAutenticacao] Token revogado para sensor: " + sensorId);
    }

    public void adicionarSensor(String sensorId, String senha) {
        senhasSensores.put(sensorId, senha);
        System.out.println("[GestorAutenticacao] Novo sensor cadastrado: " + sensorId);
    }

    public void removerSensor(String sensorId) {
        senhasSensores.remove(sensorId);
        tokensAtivos.remove(sensorId);
        rateLimiter.resetar(sensorId);
        System.out.println("[GestorAutenticacao] Sensor removido: " + sensorId);
    }

    public int getTotalSensoresAutorizados() {
        return senhasSensores.size();
    }

    public int getTotalSensoresBloqueados() {
        // Não há método público para contar bloqueios no RateLimiter
        // Retornar 0 por enquanto ou adicionar método ao RateLimiter se necessário
        return 0;
    }

    public int getTotalTokensAtivos() {
        return tokensAtivos.size();
    }
    
    public String getDiagnosticoRateLimiter() {
        return rateLimiter.diagnostico();
    }
}
