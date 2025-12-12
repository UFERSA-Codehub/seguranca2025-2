import { Link } from 'react-router-dom';
import { 
    AlertTriangle, 
    AlertCircle, 
    Check,
    Droplets,
    Thermometer,
    BarChart3,
    Radio
} from 'lucide-react';

const ALERT_CONFIG = {
    CRITICO: { 
        color: '#ef4444', 
        bgColor: '#7f1d1d', 
        Icon: AlertCircle,
        label: 'Crítico'
    },
    ATENCAO: { 
        color: '#eab308', 
        bgColor: '#713f12', 
        Icon: AlertTriangle,
        label: 'Atenção'
    },
    NORMAL: { 
        color: '#22c55e', 
        bgColor: '#14532d', 
        Icon: Check,
        label: 'Normal'
    },
};

export default function FloodReport({ data }) {
    const config = ALERT_CONFIG[data.alertLevel] || ALERT_CONFIG.NORMAL;
    const humidity = parseFloat(data.avgHumidity) || 0;
    const IconComponent = config.Icon;

    // Calculate water level visual (0-100% based on humidity)
    const waterLevel = Math.min(100, Math.max(0, humidity));

    return (
        <div className="page report-page flood-report">
            <div className="page-header">
                <Link to="/reports" className="back-link">← Voltar para Relatórios</Link>
                <h2>Alerta de Enchente</h2>
            </div>

            <div className="report-content">
                {/* Alert Level Section */}
                <div className="report-section alert-level-section">
                    <div 
                        className="alert-level-badge"
                        style={{ backgroundColor: config.bgColor, borderColor: config.color }}
                    >
                        <IconComponent size={48} color={config.color} />
                        <span className="alert-text" style={{ color: config.color }}>
                            {config.label}
                        </span>
                    </div>
                </div>

                {/* Water Level Visualization */}
                <div className="report-section water-level-section">
                    <h3>Nível de Umidade</h3>
                    <div className="water-tank">
                        <div 
                            className="water-fill"
                            style={{ 
                                height: `${waterLevel}%`,
                                backgroundColor: config.color,
                                opacity: 0.6
                            }}
                        >
                            <div className="water-wave"></div>
                        </div>
                        <div className="water-value">
                            <span className="water-percent">{data.avgHumidity}%</span>
                            <span className="water-label">Umidade Média</span>
                        </div>
                    </div>
                </div>

                {/* Recommendation */}
                <div className="report-section recommendation-section" style={{ borderLeftColor: config.color }}>
                    <h3>Recomendação</h3>
                    <p>{data.recommendation}</p>
                </div>

                {/* Stats Grid */}
                <div className="report-section">
                    <h3>Dados Meteorológicos</h3>
                    <div className="stats-grid">
                        <div className="stat-card">
                            <div className="stat-icon"><Droplets size={24} /></div>
                            <span className="stat-value">{data.avgHumidity}%</span>
                            <span className="stat-label">Umidade Média</span>
                        </div>
                        <div className="stat-card">
                            <div className="stat-icon"><Thermometer size={24} /></div>
                            <span className="stat-value">{data.avgTemperature}C</span>
                            <span className="stat-label">Temperatura Média</span>
                        </div>
                        <div className="stat-card">
                            <div className="stat-icon"><BarChart3 size={24} /></div>
                            <span className="stat-value">{data.totalReadings}</span>
                            <span className="stat-label">Leituras</span>
                        </div>
                        <div className="stat-card">
                            <div className="stat-icon"><Radio size={24} /></div>
                            <span className="stat-value">{data.totalSensors}</span>
                            <span className="stat-label">Sensores</span>
                        </div>
                    </div>
                </div>

                {/* Report Footer */}
                <div className="report-footer">
                    Gerado em: {new Date(data.generatedAt).toLocaleString('pt-BR')}
                </div>
            </div>
        </div>
    );
}
