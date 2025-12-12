import { Link } from 'react-router-dom';
import { 
    Frown, 
    Meh, 
    Smile,
    Factory,
    Circle,
    Cloud,
    BarChart3
} from 'lucide-react';

const STATUS_CONFIG = {
    RUIM: { 
        color: '#ef4444', 
        bgColor: '#7f1d1d', 
        Icon: Frown,
        label: 'Ruim'
    },
    ACEITAVEL: { 
        color: '#eab308', 
        bgColor: '#713f12', 
        Icon: Meh,
        label: 'Aceitável'
    },
    EXCELENTE: { 
        color: '#22c55e', 
        bgColor: '#14532d', 
        Icon: Smile,
        label: 'Excelente'
    },
};

export default function PollutionReport({ data }) {
    const config = STATUS_CONFIG[data.co2Status] || STATUS_CONFIG.ACEITAVEL;
    const co2Value = parseFloat(data.avgCo2) || 0;
    const IconComponent = config.Icon;

    // Find which threshold section we're in
    const getBarSegments = () => {
        const segments = [];
        let currentValue = co2Value;
        const thresholds = [450, 700, 1000, 2000, 3000];
        const colors = ['#22c55e', '#84cc16', '#eab308', '#f97316', '#ef4444'];
        
        thresholds.forEach((threshold, i) => {
            const prevThreshold = i === 0 ? 0 : thresholds[i - 1];
            const segmentSize = threshold - prevThreshold;
            const filled = Math.min(Math.max(0, currentValue - prevThreshold), segmentSize);
            const percent = (filled / segmentSize) * 100;
            segments.push({ color: colors[i], percent, threshold });
        });
        
        return segments;
    };

    return (
        <div className="page report-page pollution-report">
            <div className="page-header">
                <Link to="/reports" className="back-link">← Voltar para Relatórios</Link>
                <h2>Previsão de Poluição</h2>
            </div>

            <div className="report-content">
                {/* CO2 Status Section */}
                <div className="report-section co2-status-section">
                    <div 
                        className="co2-status-badge"
                        style={{ backgroundColor: config.bgColor, borderColor: config.color }}
                    >
                        <IconComponent size={40} color={config.color} />
                        <span className="status-text" style={{ color: config.color }}>
                            {config.label}
                        </span>
                    </div>
                    <div className="co2-value-display">
                        <span className="co2-value">{data.avgCo2}</span>
                        <span className="co2-unit">ppm CO2</span>
                    </div>
                </div>

                {/* CO2 Level Bar */}
                <div className="report-section">
                    <h3>Nível de CO2</h3>
                    <div className="level-bar-container">
                        <div className="level-bar">
                            {getBarSegments().map((seg, i) => (
                                <div 
                                    key={i} 
                                    className="level-segment"
                                    style={{ 
                                        flex: 1,
                                        backgroundColor: '#1e293b',
                                        position: 'relative',
                                        overflow: 'hidden'
                                    }}
                                >
                                    <div 
                                        className="level-fill"
                                        style={{ 
                                            width: `${seg.percent}%`,
                                            backgroundColor: seg.color,
                                            height: '100%',
                                            transition: 'width 0.5s ease'
                                        }}
                                    />
                                </div>
                            ))}
                        </div>
                        <div className="level-labels">
                            <span>0</span>
                            <span>450</span>
                            <span>700</span>
                            <span>1000</span>
                            <span>2000</span>
                            <span>3000+</span>
                        </div>
                    </div>
                </div>

                {/* Recommendation */}
                <div className="report-section recommendation-section" style={{ borderLeftColor: config.color }}>
                    <h3>Recomendação</h3>
                    <p>{data.recommendation}</p>
                </div>

                {/* Pollutant Stats */}
                <div className="report-section">
                    <h3>Concentração de Poluentes</h3>
                    <div className="stats-grid">
                        <div className="stat-card">
                            <div className="stat-icon"><Factory size={24} /></div>
                            <span className="stat-value">{data.avgCo2}</span>
                            <span className="stat-label">CO2 (ppm)</span>
                        </div>
                        <div className="stat-card">
                            <div className="stat-icon"><Circle size={24} /></div>
                            <span className="stat-value">{data.avgNo2}</span>
                            <span className="stat-label">NO2 (ppb)</span>
                        </div>
                        <div className="stat-card">
                            <div className="stat-icon"><Cloud size={24} /></div>
                            <span className="stat-value">{data.avgPm25}</span>
                            <span className="stat-label">PM2.5 (ug/m3)</span>
                        </div>
                        <div className="stat-card">
                            <div className="stat-icon"><BarChart3 size={24} /></div>
                            <span className="stat-value">{data.totalReadings}</span>
                            <span className="stat-label">Leituras</span>
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
