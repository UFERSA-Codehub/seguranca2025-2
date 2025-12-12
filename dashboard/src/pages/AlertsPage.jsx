import { useState, useEffect, useCallback } from 'react';
import { api } from '@/api/client';

const PAGE_SIZE = 15;

function AlertDetailDialog({ alert, onClose }) {
    if (!alert) return null;

    const formatTimestamp = (ts) => {
        if (!ts) return '-';
        return new Date(ts).toLocaleString('pt-BR');
    };

    const formatAlertType = (type) => {
        const typeNames = {
            'pollution': 'Poluição',
            'flood': 'Enchente',
            'noise': 'Ruído',
            'uv': 'Índice UV',
            'air-quality': 'Qualidade do Ar',
        };
        return typeNames[type?.toLowerCase()] || type || 'Alerta';
    };

    const data = alert.data || {};

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog-content" onClick={(e) => e.stopPropagation()}>
                <div className="dialog-header">
                    <h3>{formatAlertType(alert.alertType)}</h3>
                    <button className="dialog-close" onClick={onClose}>&times;</button>
                </div>
                <div className="dialog-body">
                    <div className="detail-row">
                        <span className="detail-label">Sensor:</span>
                        <span className="detail-value">{alert.sensorId || '-'}</span>
                    </div>
                    <div className="detail-row">
                        <span className="detail-label">Timestamp:</span>
                        <span className="detail-value">{formatTimestamp(alert.timestamp)}</span>
                    </div>
                    <div className="detail-row">
                        <span className="detail-label">Tipo:</span>
                        <span className="detail-value">{data.type || alert.alertType || '-'}</span>
                    </div>
                    {data.value !== undefined && (
                        <div className="detail-row">
                            <span className="detail-label">Valor:</span>
                            <span className="detail-value">{data.value} {data.unit || ''}</span>
                        </div>
                    )}
                    {data.latitude !== undefined && data.longitude !== undefined && (
                        <div className="detail-row">
                            <span className="detail-label">Localização:</span>
                            <span className="detail-value">{data.latitude}, {data.longitude}</span>
                        </div>
                    )}
                    <div className="detail-row">
                        <span className="detail-label">Dados Completos:</span>
                    </div>
                    <pre className="detail-json">{JSON.stringify(data, null, 2)}</pre>
                </div>
            </div>
        </div>
    );
}

export default function AlertsPage() {
    const [alerts, setAlerts] = useState([]);
    const [page, setPage] = useState(1);
    const [totalPages, setTotalPages] = useState(1);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [selectedAlert, setSelectedAlert] = useState(null);

    const fetchAlerts = useCallback(async () => {
        try {
            setLoading(true);
            const result = await api.alerts(page, PAGE_SIZE);
            // API returns 'alerts' array, not 'data'
            setAlerts(result.alerts || []);
            setTotalPages(result.totalPages || 1);
            setError(null);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }, [page]);

    useEffect(() => {
        fetchAlerts();
    }, [fetchAlerts]);

    const formatTimestamp = (ts) => {
        if (!ts) return '-';
        return new Date(ts).toLocaleString('pt-BR');
    };

    const getSeverityFromAlertType = (alertType) => {
        // Map alert types to severity levels
        const severityMap = {
            'pollution': 'high',
            'flood': 'critical',
            'noise': 'medium',
            'uv': 'medium',
            'air-quality': 'high',
        };
        return severityMap[alertType?.toLowerCase()] || 'medium';
    };

    const getSeverityClass = (severity) => {
        switch (severity?.toLowerCase()) {
            case 'critical': return 'severity-critical';
            case 'high': return 'severity-high';
            case 'medium': return 'severity-medium';
            case 'low': return 'severity-low';
            default: return '';
        }
    };

    const formatAlertType = (type) => {
        const typeNames = {
            'pollution': 'Poluição',
            'flood': 'Enchente',
            'noise': 'Ruído',
            'uv': 'Índice UV',
            'air-quality': 'Qualidade do Ar',
        };
        return typeNames[type?.toLowerCase()] || type || 'Alerta';
    };

    const getAlertMessage = (alert) => {
        // Extract message from the alert data
        const data = alert.data || {};
        const type = data.type || alert.alertType || 'unknown';
        const value = data.value;
        const unit = data.unit || '';
        
        if (value !== undefined) {
            return `Valor detectado: ${value} ${unit}`;
        }
        return `Alerta do tipo ${formatAlertType(type)}`;
    };

    return (
        <div className="page alerts-page">
            <h2>Alertas</h2>

            {error && <div className="error-text">{error}</div>}

            {alerts.length > 0 && (
                <>
                    <div className="alerts-list">
                        {alerts.map((alert, idx) => {
                            const severity = getSeverityFromAlertType(alert.alertType);
                            return (
                                <div 
                                    key={`${alert.sensorId}-${alert.timestamp}-${idx}`} 
                                    className={`alert-card ${getSeverityClass(severity)}`}
                                    onClick={() => setSelectedAlert(alert)}
                                >
                                    <div className="alert-header">
                                        <span className="alert-type">{formatAlertType(alert.alertType)}</span>
                                        <span className="alert-severity">{severity}</span>
                                    </div>
                                    <div className="alert-message">{getAlertMessage(alert)}</div>
                                    <div className="alert-meta">
                                        <span className="alert-source">{alert.sensorId || '-'}</span>
                                        <span className="alert-time">{formatTimestamp(alert.timestamp)}</span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <div className="pagination">
                        <button
                            onClick={() => setPage(1)}
                            disabled={page === 1}
                        >
                            Primeira
                        </button>
                        <button
                            onClick={() => setPage((p) => Math.max(1, p - 1))}
                            disabled={page === 1}
                        >
                            Anterior
                        </button>
                        <span className="page-info">
                            Página {page} de {totalPages}
                        </span>
                        <button
                            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                            disabled={page === totalPages}
                        >
                            Próxima
                        </button>
                        <button
                            onClick={() => setPage(totalPages)}
                            disabled={page === totalPages}
                        >
                            Última
                        </button>
                    </div>
                </>
            )}

            {!loading && alerts.length === 0 && !error && (
                <div className="empty-state">Nenhum alerta no momento</div>
            )}

            <AlertDetailDialog 
                alert={selectedAlert} 
                onClose={() => setSelectedAlert(null)} 
            />
        </div>
    );
}
