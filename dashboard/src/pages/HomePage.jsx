import { useState, useEffect, useCallback, useRef } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/contexts/AuthContext';
import { useTrace } from '@/contexts/TraceContext';

const POLL_INTERVAL_MS = 60000; // 60 seconds

// Alert type formatting (same as AlertsPage)
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

// Severity mapping (same as AlertsPage)
const getSeverityFromAlertType = (alertType) => {
    const severityMap = {
        'pollution': 'high',
        'flood': 'critical',
        'noise': 'medium',
        'uv': 'medium',
        'air-quality': 'high',
    };
    return severityMap[alertType?.toLowerCase()] || 'medium';
};

// Alert message builder (same as AlertsPage)
const getAlertMessage = (alert) => {
    const data = alert.data || {};
    const type = data.type || alert.alertType || 'unknown';
    const value = data.value;
    const unit = data.unit || '';
    
    if (value !== undefined) {
        return `Valor detectado: ${value} ${unit}`;
    }
    return `Alerta do tipo ${formatAlertType(type)}`;
};

// Alert detail dialog (same as AlertsPage)
function AlertDetailDialog({ alert, onClose }) {
    if (!alert) return null;

    const formatTimestamp = (ts) => {
        if (!ts) return '-';
        return new Date(ts).toLocaleString('pt-BR');
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

export default function HomePage() {
    const { user } = useAuth();
    const [status, setStatus] = useState(null);
    const [alerts, setAlerts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [lastUpdate, setLastUpdate] = useState(null);
    const [selectedAlert, setSelectedAlert] = useState(null);
    
    // Get trace connection status for indicator
    const { connected: traceConnected, allEvents } = useTrace();
    const pollTimerRef = useRef(null);
    
    // Store previous trace stats to show polled values
    const [traceStats, setTraceStats] = useState({
        totalEvents: 0,
        tcpEvents: 0,
        udpEvents: 0,
    });

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            // Fetch status and alerts in parallel
            const [statusData, alertsData] = await Promise.all([
                api.status(),
                api.alerts(1, 3), // Get last 3 alerts
            ]);
            setStatus(statusData);
            setAlerts(alertsData.alerts || []);
            
            // Update trace stats from current allEvents
            setTraceStats({
                totalEvents: allEvents.length,
                tcpEvents: allEvents.filter(e => e.protocol === 'TCP').length,
                udpEvents: allEvents.filter(e => e.protocol === 'UDP').length,
            });
            
            setLastUpdate(new Date());
            setError(null);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }, [allEvents]);

    // Fetch on mount and poll every 60 seconds
    useEffect(() => {
        fetchData();
        
        pollTimerRef.current = setInterval(fetchData, POLL_INTERVAL_MS);
        
        return () => {
            if (pollTimerRef.current) {
                clearInterval(pollTimerRef.current);
            }
        };
    }, [fetchData]);

    const formatLastUpdate = () => {
        if (!lastUpdate) return '-';
        return lastUpdate.toLocaleTimeString('pt-BR');
    };

    const formatTimestamp = (ts) => {
        if (!ts) return '-';
        return new Date(ts).toLocaleString('pt-BR');
    };

    return (
        <div className="page home-page">
            <h2>Bem-vindo, {user?.username}</h2>

            {error && <div className="error-text">{error}</div>}

            {status && (
                <div className="status-content">
                    {/* Backend Stats */}
                    <div className="status-section">
                        <h3>Dados do Servidor</h3>
                        <div className="stats-grid">
                            <div className="stat-card">
                                <span className="stat-value">{status.sensors ?? 0}</span>
                                <span className="stat-label">Sensores Ativos</span>
                            </div>
                            <div className="stat-card">
                                <span className="stat-value">{status.readings ?? 0}</span>
                                <span className="stat-label">Total de Leituras</span>
                            </div>
                            <div className="stat-card alert">
                                <span className="stat-value">{status.alerts ?? 0}</span>
                                <span className="stat-label">Alertas</span>
                            </div>
                        </div>
                    </div>

                    {/* Trace Stats - polled every 60s */}
                    <div className="status-section">
                        <h3>Estatísticas de Trace</h3>
                        <div className="stats-grid">
                            <div className={`stat-card ${traceConnected ? 'success' : 'warning'}`}>
                                <span className="stat-value">{traceConnected ? 'Online' : 'Offline'}</span>
                                <span className="stat-label">WebSocket</span>
                            </div>
                            <div className="stat-card">
                                <span className="stat-value">{traceStats.totalEvents}</span>
                                <span className="stat-label">Eventos Capturados</span>
                            </div>
                            <div className="stat-card">
                                <span className="stat-value">{traceStats.tcpEvents}</span>
                                <span className="stat-label">Eventos TCP</span>
                            </div>
                            <div className="stat-card">
                                <span className="stat-value">{traceStats.udpEvents}</span>
                                <span className="stat-label">Eventos UDP</span>
                            </div>
                        </div>
                    </div>

                    {/* Latest Alerts */}
                    {alerts.length > 0 && (
                        <div className="status-section">
                            <h3>Últimos Alertas</h3>
                            <div className="alerts-list">
                                {alerts.map((alert, idx) => {
                                    const severity = getSeverityFromAlertType(alert.alertType);
                                    return (
                                        <div 
                                            key={`${alert.sensorId}-${alert.timestamp}-${idx}`}
                                            className={`alert-card severity-${severity}`}
                                            onClick={() => setSelectedAlert(alert)}
                                        >
                                            <div className="alert-header">
                                                <span className="alert-type">{formatAlertType(alert.alertType)}</span>
                                                <span className="alert-severity">{severity.toUpperCase()}</span>
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
                        </div>
                    )}

                    <div className="last-updated">
                        Última atualização: {formatLastUpdate()} (atualiza a cada 60s)
                    </div>
                </div>
            )}

            <AlertDetailDialog 
                alert={selectedAlert} 
                onClose={() => setSelectedAlert(null)} 
            />
        </div>
    );
}
