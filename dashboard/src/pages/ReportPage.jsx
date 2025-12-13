import { lazy, Suspense, useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '@/api/client';
const AirQualityReport = lazy(() => import('./reports/AirQualityReport'));
const FloodReport = lazy(() => import('./reports/FloodReport'));
const PollutionReport = lazy(() => import('./reports/PollutionReport'));
const NoiseReport = lazy(() => import('./reports/NoiseReport'));
const UVReport = lazy(() => import('./reports/UVReport'));
const REPORT_COMPONENTS = {
    'air-quality': AirQualityReport,
    'flood': FloodReport,
    'pollution': PollutionReport,
    'noise': NoiseReport,
    'uv': UVReport,
};
const REPORT_TITLES = {
    'air-quality': 'Qualidade do Ar',
    'flood': 'Alerta de Enchente',
    'pollution': 'Previsão de Poluição',
    'noise': 'Mapa de Ruído',
    'uv': 'Índice UV',
};
function ReportLoading() {
    return <div className="loading-text">Carregando visualização...</div>;
}
export default function ReportPage() {
    const { type } = useParams();
    const [report, setReport] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    useEffect(() => {
        setLoading(true);
        setError(null);
        api.report(type)
            .then(setReport)
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false));
    }, [type]);
    const ReportComponent = REPORT_COMPONENTS[type];
    if (loading) {
        return (
            <div className="page report-page">
                <div className="page-header">
                    <Link to="/reports" className="back-link">← Voltar para Relatórios</Link>
                    <h2>{REPORT_TITLES[type] || 'Relatório'}</h2>
                </div>
                <div className="loading-text">Carregando relatório...</div>
            </div>
        );
    }
    if (error) {
        return (
            <div className="page report-page">
                <div className="page-header">
                    <Link to="/reports" className="back-link">← Voltar para Relatórios</Link>
                    <h2>{REPORT_TITLES[type] || 'Relatório'}</h2>
                </div>
                <div className="error-text">{error}</div>
            </div>
        );
    }
    if (!ReportComponent) {
        return (
            <div className="page report-page">
                <div className="page-header">
                    <Link to="/reports" className="back-link">← Voltar para Relatórios</Link>
                    <h2>Relatório Desconhecido</h2>
                </div>
                <div className="error-text">Tipo de relatório não encontrado: {type}</div>
            </div>
        );
    }
    return (
        <Suspense fallback={<ReportLoading />}>
            <ReportComponent data={report} />
        </Suspense>
    );
}
