import { Link } from 'react-router-dom';
import { 
    Volume2, 
    Volume1, 
    VolumeX,
    SlidersHorizontal,
    BarChart3,
    Radio,
    BookOpen,
    Briefcase,
    Car,
    Factory,
    Music
} from 'lucide-react';

const NOISE_CONFIG = {
    ALTO: { 
        color: '#ef4444', 
        bgColor: '#7f1d1d', 
        Icon: Volume2,
        label: 'Alto',
        description: 'Nível prejudicial à saúde'
    },
    MODERADO: { 
        color: '#eab308', 
        bgColor: '#713f12', 
        Icon: Volume1,
        label: 'Moderado',
        description: 'Nível aceitável para trabalho'
    },
    BAIXO: { 
        color: '#22c55e', 
        bgColor: '#14532d', 
        Icon: VolumeX,
        label: 'Baixo',
        description: 'Ambiente silencioso'
    },
};

// Reference noise levels in dB
const NOISE_REFERENCES = [
    { level: 30, label: 'Biblioteca', Icon: BookOpen },
    { level: 50, label: 'Escritório', Icon: Briefcase },
    { level: 70, label: 'Trânsito', Icon: Car },
    { level: 85, label: 'Fábrica', Icon: Factory },
    { level: 100, label: 'Show', Icon: Music },
];

export default function NoiseReport({ data }) {
    const config = NOISE_CONFIG[data.noiseLevel] || NOISE_CONFIG.MODERADO;
    const noiseValue = parseFloat(data.avgNoise) || 0;
    const IconComponent = config.Icon;

    // Calculate position on the noise meter (0-120 dB scale)
    const meterPercent = Math.min(100, (noiseValue / 120) * 100);

    // Find closest reference
    const closestRef = NOISE_REFERENCES.reduce((prev, curr) => 
        Math.abs(curr.level - noiseValue) < Math.abs(prev.level - noiseValue) ? curr : prev
    );

    return (
        <div className="page report-page noise-report">
            <div className="page-header">
                <Link to="/reports" className="back-link">← Voltar para Relatórios</Link>
                <h2>Mapa de Ruído</h2>
            </div>

            <div className="report-content">
                {/* Noise Level Display */}
                <div className="report-section noise-level-section">
                    <div className="noise-display">
                        <div 
                            className="noise-badge"
                            style={{ backgroundColor: config.bgColor, borderColor: config.color }}
                        >
                            <IconComponent size={48} color={config.color} />
                        </div>
                        <div className="noise-value-container">
                            <span className="noise-value" style={{ color: config.color }}>
                                {data.avgNoise}
                            </span>
                            <span className="noise-unit">dB</span>
                        </div>
                        <div className="noise-classification" style={{ color: config.color }}>
                            {config.label}
                        </div>
                        <div className="noise-description">
                            {config.description}
                        </div>
                    </div>
                </div>

                {/* Noise Meter */}
                <div className="report-section">
                    <h3>Escala de Ruído</h3>
                    <div className="noise-meter-container">
                        <div className="noise-meter">
                            <div className="meter-gradient"></div>
                            <div 
                                className="meter-indicator"
                                style={{ left: `${meterPercent}%` }}
                            >
                                <div className="indicator-line"></div>
                                <div className="indicator-value">{data.avgNoise} dB</div>
                            </div>
                        </div>
                        <div className="meter-labels">
                            <span>0</span>
                            <span>30</span>
                            <span>50</span>
                            <span>70</span>
                            <span>85</span>
                            <span>120</span>
                        </div>
                    </div>
                </div>

                {/* Reference Comparison */}
                <div className="report-section">
                    <h3>Comparação com Referências</h3>
                    <div className="noise-references">
                        {NOISE_REFERENCES.map(ref => {
                            const RefIcon = ref.Icon;
                            return (
                                <div 
                                    key={ref.level} 
                                    className={`reference-item ${ref.level === closestRef.level ? 'active' : ''}`}
                                    style={ref.level === closestRef.level ? { borderColor: config.color } : {}}
                                >
                                    <RefIcon size={24} />
                                    <span className="reference-label">{ref.label}</span>
                                    <span className="reference-level">{ref.level} dB</span>
                                </div>
                            );
                        })}
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
                            <div className="stat-icon"><SlidersHorizontal size={24} /></div>
                            <span className="stat-value">{data.avgNoise} dB</span>
                            <span className="stat-label">Nível Médio</span>
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
