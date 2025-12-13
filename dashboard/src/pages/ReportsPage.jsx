import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '@/api/client';
import { Wind, Droplets, Volume2, Factory, Sun } from 'lucide-react';
const REPORT_METADATA = {
    'air-quality': {
        name: 'Qualidade do Ar',
        description: 'Índice de qualidade do ar e poluentes',
        Icon: Wind,
    },
    'flood': {
        name: 'Enchente',
        description: 'Níveis de água e risco de inundação',
        Icon: Droplets,
    },
    'noise': {
        name: 'Ruído',
        description: 'Níveis de poluição sonora',
        Icon: Volume2,
    },
    'pollution': {
        name: 'Poluição',
        description: 'Níveis de poluição ambiental',
        Icon: Factory,
    },
    'uv': {
        name: 'Índice UV',
        description: 'Radiação ultravioleta',
        Icon: Sun,
    },
};
export default function ReportsPage() {
    const [reports, setReports] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    useEffect(() => {
        api.reports()
            .then((data) => {
                const reportList = (data.reports || []).map((item) => {
                    const type = typeof item === 'string' ? item : item.type;
                    const metadata = REPORT_METADATA[type] || {};
                    return {
                        type,
                        name: metadata.name || type,
                        description: metadata.description || '',
                        Icon: metadata.Icon,
                    };
                });
                setReports(reportList);
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false));
    }, []);
    return (
        <div className="page reports-page">
            <h2>Relatórios</h2>
            {loading && <div className="loading-text">Carregando relatórios...</div>}
            {error && <div className="error-text">{error}</div>}
            {reports.length > 0 && (
                <div className="reports-grid">
                    {reports.map((report) => {
                        const IconComponent = report.Icon;
                        return (
                            <Link
                                key={report.type}
                                to={`/reports/${report.type}`}
                                className="report-card"
                            >
                                <span className="report-icon">
                                    {IconComponent ? <IconComponent size={32} /> : null}
                                </span>
                                <span className="report-name">{report.name || report.type}</span>
                                <span className="report-desc">{report.description || ''}</span>
                            </Link>
                        );
                    })}
                </div>
            )}
            {!loading && reports.length === 0 && !error && (
                <div className="empty-state">Nenhum relatório disponível</div>
            )}
        </div>
    );
}
