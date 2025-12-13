import { Link } from 'react-router-dom';
import { 
    Sun, 
    CloudSun, 
    Zap, 
    AlertTriangle,
    Radiation,
    BarChart3,
    Radio,
    ShieldCheck,
    Glasses,
    HardHat,
    Home
} from 'lucide-react';

const UV_CONFIG = {
    BAIXO: { 
        color: '#22c55e', 
        bgColor: '#14532d', 
        Icon: Sun,
        label: 'Baixo',
        protection: 'Proteção mínima necessária'
    },
    MODERADO: { 
        color: '#eab308', 
        bgColor: '#713f12', 
        Icon: CloudSun,
        label: 'Moderado',
        protection: 'Use óculos de sol e protetor solar'
    },
    ALTO: { 
        color: '#f97316', 
        bgColor: '#7c2d12', 
        Icon: Zap,
        label: 'Alto',
        protection: 'Evite exposição prolongada'
    },
    'MUITO ALTO': { 
        color: '#ef4444', 
        bgColor: '#7f1d1d', 
        Icon: AlertTriangle,
        label: 'Muito Alto',
        protection: 'Proteção extra necessária'
    },
    EXTREMO: { 
        color: '#a855f7', 
        bgColor: '#581c87', 
        Icon: Radiation,
        label: 'Extremo',
        protection: 'Evite exposição ao sol'
    },
};

export default function UVReport({ data }) {
    // Handle case variations from backend
    const uvLevel = data.uvLevel?.toUpperCase().replace('-', ' ') || 'MODERADO';
    const config = UV_CONFIG[uvLevel] || UV_CONFIG.MODERADO;
    const uvValue = parseFloat(data.avgUv) || 0;
    const IconComponent = config.Icon;

    // Calculate sun intensity animation
    const sunIntensity = Math.min(1, uvValue / 11);

    // Calculate arc position (0-14 scale for UV)
    const arcPercent = Math.min(100, (uvValue / 14) * 100);

    return (
        <div className="page report-page uv-report">
            <div className="page-header">
                <Link to="/reports" className="back-link">← Voltar para Relatórios</Link>
                <h2>Índice UV</h2>
            </div>

            <div className="report-content">
                {/* UV Sun Display */}
                <div className="report-section uv-display-section">
                    <div className="uv-sun-container">
                        <div 
                            className="uv-sun"
                            style={{ 
                                '--sun-color': config.color,
                                '--sun-intensity': sunIntensity 
                            }}
                        >
                            <div className="sun-core">
                                <IconComponent size={64} color={config.color} />
                            </div>
                            <div className="sun-rays"></div>
                        </div>
                        <div className="uv-value-display">
                            <span className="uv-value" style={{ color: config.color }}>
                                {data.avgUv}
                            </span>
                            <span className="uv-label">Índice UV</span>
                        </div>
                        <div 
                            className="uv-level-badge"
                            style={{ backgroundColor: config.bgColor, color: config.color }}
                        >
                            {config.label}
                        </div>
                    </div>
                </div>

                {/* UV Scale */}
                <div className="report-section">
                    <h3>Escala de Índice UV</h3>
                    <div className="uv-scale-container">
                        <div className="uv-scale">
                            <div className="uv-scale-gradient"></div>
                            <div 
                                className="uv-scale-indicator"
                                style={{ left: `${arcPercent}%` }}
                            >
                                <div className="indicator-dot" style={{ backgroundColor: config.color }}></div>
                            </div>
                        </div>
                        <div className="uv-scale-labels">
                            {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14].map(v => (
                                <span key={v} className={v === Math.round(uvValue) ? 'active' : ''}>
                                    {v}
                                </span>
                            ))}
                        </div>
                        <div className="uv-scale-categories">
                            <span style={{ color: '#22c55e' }}>Baixo</span>
                            <span style={{ color: '#eab308' }}>Moderado</span>
                            <span style={{ color: '#f97316' }}>Alto</span>
                            <span style={{ color: '#ef4444' }}>Muito Alto</span>
                            <span style={{ color: '#a855f7' }}>Extremo</span>
                        </div>
                    </div>
                </div>

                {/* Protection Info */}
                <div className="report-section protection-section">
                    <h3>Proteção Recomendada</h3>
                    <div className="protection-grid">
                        <div className={`protection-item ${uvValue >= 3 ? 'recommended' : ''}`}>
                            <ShieldCheck size={32} />
                            <span className="protection-label">Protetor Solar</span>
                        </div>
                        <div className={`protection-item ${uvValue >= 3 ? 'recommended' : ''}`}>
                            <Glasses size={32} />
                            <span className="protection-label">Óculos de Sol</span>
                        </div>
                        <div className={`protection-item ${uvValue >= 6 ? 'recommended' : ''}`}>
                            <HardHat size={32} />
                            <span className="protection-label">Chapéu</span>
                        </div>
                        <div className={`protection-item ${uvValue >= 8 ? 'recommended' : ''}`}>
                            <Home size={32} />
                            <span className="protection-label">Buscar Sombra</span>
                        </div>
                    </div>
                </div>

                {/* Recommendation */}
                <div className="report-section recommendation-section" style={{ borderLeftColor: config.color }}>
                    <h3>Recomendação</h3>
                    <p>{data.recommendation}</p>
                </div>

                {/* Stats */}
                <div className="report-section">
                    <div className="stats-grid">
                        <div className="stat-card">
                            <div className="stat-icon"><Sun size={24} /></div>
                            <span className="stat-value">{data.avgUv}</span>
                            <span className="stat-label">Índice UV Médio</span>
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
