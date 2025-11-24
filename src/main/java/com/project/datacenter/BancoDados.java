package com.project.datacenter;

import com.project.model.DadosAmbientais;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BancoDados {
    private Connection conexao;                    // Conexão SQLite
    private String caminhoBanco;                   // Caminho do arquivo .db

    public BancoDados(String caminhoBanco) {
        this.caminhoBanco = caminhoBanco;
    }

    public void inicializar() {
        try {
            Class.forName("org.sqlite.JDBC");
            conexao = DriverManager.getConnection("jdbc:sqlite:" + caminhoBanco);
            
            criarTabelas();
            
            System.out.println("[BancoDados] ✅ Banco de dados inicializado: " + caminhoBanco);
            
        } catch (Exception e) {
            System.err.println("[BancoDados] ❌ Erro ao inicializar banco: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void criarTabelas() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS leituras (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sensor_id TEXT NOT NULL,
                timestamp BIGINT NOT NULL,
                localizacao TEXT NOT NULL,
                temperatura REAL,
                co2 REAL,
                umidade REAL,
                ruido REAL,
                radiacao_uv REAL,
                pm25 REAL,
                pm10 REAL
            );
            
            CREATE INDEX IF NOT EXISTS idx_sensor ON leituras(sensor_id);
            CREATE INDEX IF NOT EXISTS idx_timestamp ON leituras(timestamp);
            CREATE INDEX IF NOT EXISTS idx_localizacao ON leituras(localizacao);
        """;

        try (Statement stmt = conexao.createStatement()) {
            for (String comando : sql.split(";")) {
                if (!comando.trim().isEmpty()) {
                    stmt.executeUpdate(comando);
                }
            }
            System.out.println("[BancoDados] Tabelas criadas/verificadas");
        }
    }

    public synchronized void inserirLeitura(DadosAmbientais dados) {
        inserirLeituraComSensor("UNKNOWN", dados);
    }

    public synchronized void inserirLeituraComSensor(String sensorId, DadosAmbientais dados) {
        String sql = """
            INSERT INTO leituras (sensor_id, timestamp, localizacao, temperatura, co2, 
                                  umidade, ruido, radiacao_uv, pm25, pm10)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            pstmt.setString(1, sensorId);
            pstmt.setLong(2, dados.getTimestamp());
            pstmt.setString(3, dados.getLocalizacao());
            pstmt.setDouble(4, dados.getTemperatura());
            pstmt.setDouble(5, dados.getCo2());
            pstmt.setDouble(6, dados.getUmidade());
            pstmt.setDouble(7, dados.getRuido());
            pstmt.setDouble(8, dados.getRadiacao_uv());
            pstmt.setDouble(9, dados.getPm25());
            pstmt.setDouble(10, dados.getPm10());

            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao inserir leitura: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<DadosAmbientais> buscarPorPeriodo(long timestampInicio, long timestampFim) {
        String sql = "SELECT * FROM leituras WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp ASC";
        List<DadosAmbientais> resultados = new ArrayList<>();

        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            pstmt.setLong(1, timestampInicio);
            pstmt.setLong(2, timestampFim);

            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                resultados.add(extrairDadosAmbientais(rs));
            }

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao buscar por período: " + e.getMessage());
            e.printStackTrace();
        }

        return resultados;
    }

    public List<DadosAmbientais> buscarPorSensor(String sensorId) {
        String sql = "SELECT * FROM leituras WHERE sensor_id = ? ORDER BY timestamp ASC";
        List<DadosAmbientais> resultados = new ArrayList<>();

        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            pstmt.setString(1, sensorId);

            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                resultados.add(extrairDadosAmbientais(rs));
            }

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao buscar por sensor: " + e.getMessage());
            e.printStackTrace();
        }

        return resultados;
    }

    public List<DadosAmbientais> consultarPorPeriodo(long timestampInicio, long timestampFim) {
        return buscarPorPeriodo(timestampInicio, timestampFim);
    }

    public List<DadosAmbientais> consultarPorSensor(String sensorId, long timestampInicio, long timestampFim) {
        String sql = "SELECT * FROM leituras WHERE sensor_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC";
        List<DadosAmbientais> resultados = new ArrayList<>();

        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            pstmt.setString(1, sensorId);
            pstmt.setLong(2, timestampInicio);
            pstmt.setLong(3, timestampFim);

            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                resultados.add(extrairDadosAmbientais(rs));
            }

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao consultar por sensor e período: " + e.getMessage());
            e.printStackTrace();
        }

        return resultados;
    }

    public List<DadosAmbientais> consultarPorLocalizacao(String localizacao, long timestampInicio, long timestampFim) {
        String sql = "SELECT * FROM leituras WHERE localizacao = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC";
        List<DadosAmbientais> resultados = new ArrayList<>();

        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            pstmt.setString(1, localizacao);
            pstmt.setLong(2, timestampInicio);
            pstmt.setLong(3, timestampFim);

            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                resultados.add(extrairDadosAmbientais(rs));
            }

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao consultar por localização: " + e.getMessage());
            e.printStackTrace();
        }

        return resultados;
    }

    public int contarPorSensor() {
        String sql = "SELECT COUNT(DISTINCT sensor_id) as total FROM leituras";
        
        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao contar sensores: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    public List<DadosAmbientais> buscarTodas(int limite) {
        String sql = "SELECT * FROM leituras ORDER BY timestamp DESC LIMIT ?";
        List<DadosAmbientais> resultados = new ArrayList<>();

        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            pstmt.setInt(1, limite);

            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                resultados.add(extrairDadosAmbientais(rs));
            }

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao buscar todas: " + e.getMessage());
            e.printStackTrace();
        }

        return resultados;
    }

    public List<String> listarSensores() {
        String sql = "SELECT DISTINCT sensor_id FROM leituras ORDER BY sensor_id";
        List<String> sensores = new ArrayList<>();

        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                sensores.add(rs.getString("sensor_id"));
            }

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao listar sensores: " + e.getMessage());
            e.printStackTrace();
        }

        return sensores;
    }

    public int contarLeituras() {
        String sql = "SELECT COUNT(*) as total FROM leituras";
        
        try (PreparedStatement pstmt = conexao.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao contar leituras: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    private DadosAmbientais extrairDadosAmbientais(ResultSet rs) throws SQLException {
        return new DadosAmbientais(
            rs.getLong("timestamp"),
            rs.getString("localizacao"),
            rs.getDouble("temperatura"),
            rs.getDouble("co2"),
            rs.getDouble("umidade"),
            rs.getDouble("ruido"),
            rs.getDouble("radiacao_uv"),
            rs.getDouble("pm25"),
            rs.getDouble("pm10")
        );
    }

    public void fechar() {
        try {
            if (conexao != null && !conexao.isClosed()) {
                conexao.close();
                System.out.println("[BancoDados] Conexão fechada");
            }
        } catch (SQLException e) {
            System.err.println("[BancoDados] Erro ao fechar conexão: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isConectado() {
        try {
            return conexao != null && !conexao.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
