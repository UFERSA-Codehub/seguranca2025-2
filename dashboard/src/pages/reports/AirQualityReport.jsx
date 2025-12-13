import { Link } from 'react-router-dom';
import { 
    Check, 
    AlertTriangle, 
    AlertCircle, 
    XCircle, 
    Skull,
    Wind,
    Gauge,
    Radio
} from 'lucide-react';

const IQA_CONFIG = {
    BOA: { color: '#22c55e', bgColor: '#14532d', label: 'Boa', Icon: Check },
    MODERADA: { color: '#eab308', bgColor: '#713f12', label: 'Moderada', Icon: AlertTriangle },
    RUIM: { color: '#f97316', bgColor: '#7c2d12', label: 'Ruim', Icon: AlertCircle },
    'MUITO RUIM': { color: '#ef4444', bgColor: '#7f1d1d', label: 'Muito Ruim', Icon: XCircle },
    'PÉSSIMA': { color: '#dc2626', bgColor: '#450a0a', label: 'Péssima', Icon: Skull },
};

const POLLUTANT_LABELS = {
    pm25: 'PM2.5',
    pm10: 'PM10',
    co: 'CO',
    no2: 'NO2',
    so2: 'SO2',
};

const POLLUTANT_UNITS = {
    pm25: 'ug/m3',
    pm10: 'ug/m3',
    co: 'ppm',
    no2: 'ppb',
    so2: 'ppb',
};

export default function AirQualityReport({ data }) {
    const config = IQA_CONFIG[data.iqaClassification] || IQA_CONFIG.BOA;
    const iqaValue = parseInt(data.iqaValue) || 0;
    const gaugePercent = Math.min(100, (iqaValue / 200) * 100);
    const IconComponent = config.Icon;

    return (
        <div className="page report-page air-quality-report">
            <div className="page-header">
                <Link to="/reports" className="back-link">← Voltar para Relatórios</Link>
                <h2>Qualidade do Ar</h2>
            </div>

            <div className="report-content">
                {/* IQA Gauge Section */}
                <div className="report-section iqa-section">
                    <div className="iqa-gauge-container">
                        <div className="iqa-gauge">
                            <svg viewBox="0 0 200 120" className="gauge-svg">
                                {/* Background arc */}
                                <path
                                    d="M 20 100 A 80 80 0 0 1 180 100"
                                    fill="none"
                                    stroke="#334155"
                                    strokeWidth="16"
                                    strokeLinecap="round"
                                />
                                {/* Colored arc based on IQA */}
                                <path
                                    d="M 20 100 A 80 80 0 0 1 180 100"
                                    fill="none"
                                    stroke={config.color}
                                    strokeWidth="16"
                                    strokeLinecap="round"
                                    strokeDasharray={`${gaugePercent * 2.51} 251`}
                                    style={{ transition: 'stroke-dasharray 0.5s ease' }}
                                />
                            </svg>
                            <div className="iqa-value" style={{ color: config.color }}>
                                {iqaValue}
                            </div>
                        </div>
                        <div 
                            className="iqa-classification"
                            style={{ backgroundColor: config.bgColor, color: config.color }}
                        >
                            <IconComponent size={20} />
                            <span className="iqa-label">{config.label}</span>
                        </div>
                    </div>
                    <div className="iqa-details">
                        <div className="detail-item">
                            <span className="detail-label">Poluente Principal</span>
                            <span className="detail-value pollutant-badge">{data.mainPollutant}</span>
                        </div>
                        <div className="detail-item">
                            <span className="detail-label">Total de Leituras</span>
                            <span className="detail-value">{data.totalReadings}</span>
                        </div>
                        <div className="detail-item">
                            <span className="detail-label">Sensores Ativos</span>
                            <span className="detail-value">{data.totalSensors}</span>
                        </div>
                    </div>
                </div>

                {/* Recommendation */}
                <div className="report-section recommendation-section" style={{ borderLeftColor: config.color }}>
                    <h3>Recomendação</h3>
                    <p>{data.recommendation}</p>
                </div>

                {/* Pollutant Cards */}
                <div className="report-section">
                    <h3>Concentração de Poluentes</h3>
                    <div className="pollutant-grid">
                        {['pm25', 'pm10', 'co', 'no2', 'so2'].map(key => {
                            const avgKey = `avg${key.charAt(0).toUpperCase() + key.slice(1)}`;
                            const value = data[avgKey] || data[`avg${key.toUpperCase()}`] || '-';
                            return (
                                <div key={key} className="pollutant-card">
                                    <div className="pollutant-name">{POLLUTANT_LABELS[key]}</div>
                                    <div className="pollutant-value">{value}</div>
                                    <div className="pollutant-unit">{POLLUTANT_UNITS[key]}</div>
                                </div>
                            );
                        })}
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
