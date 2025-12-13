import { useState, useEffect, useCallback } from 'react';
import { api } from '@/api/client';

const PAGE_SIZE = 15;

export default function DataPage() {
    const [data, setData] = useState([]);
    const [page, setPage] = useState(1);
    const [totalPages, setTotalPages] = useState(1);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            const result = await api.data(page, PAGE_SIZE);
            setData(result.data || []);
            setTotalPages(result.totalPages || 1);
            setError(null);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }, [page]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const formatTimestamp = (ts) => {
        if (!ts) return '-';
        return new Date(ts).toLocaleString('pt-BR');
    };

    // Format number with 1 decimal place
    const formatNumber = (val) => {
        if (val === undefined || val === null) return '-';
        if (typeof val === 'number') {
            return val.toFixed(1);
        }
        return val;
    };

    // Extract data fields from the nested data object
    const getData = (row) => row.data || {};

    return (
        <div className="page data-page">
            <h2>Dados dos Sensores</h2>

            {error && <div className="error-text">{error}</div>}

            {data.length > 0 && (
                <>
                    <div className="data-table-container">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>Timestamp</th>
                                    <th>Sensor</th>
                                    <th>Temp (°C)</th>
                                    <th>Umid (%)</th>
                                    <th>PM2.5</th>
                                    <th>PM10</th>
                                    <th>CO₂</th>
                                    <th>Ruído (dB)</th>
                                    <th>UV</th>
                                </tr>
                            </thead>
                            <tbody>
                                {data.map((row, idx) => {
                                    const d = getData(row);
                                    return (
                                        <tr key={`${row.sensorId}-${row.timestamp}-${idx}`}>
                                            <td>{formatTimestamp(row.timestamp)}</td>
                                            <td>{row.sensorId || '-'}</td>
                                            <td className="value-cell">{formatNumber(d.temperature)}</td>
                                            <td className="value-cell">{formatNumber(d.humidity)}</td>
                                            <td className="value-cell">{formatNumber(d.pm25)}</td>
                                            <td className="value-cell">{formatNumber(d.pm10)}</td>
                                            <td className="value-cell">{formatNumber(d.co2)}</td>
                                            <td className="value-cell">{formatNumber(d.noiseLevel)}</td>
                                            <td className="value-cell">{formatNumber(d.uvIndex)}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
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

            {!loading && data.length === 0 && !error && (
                <div className="empty-state">Nenhum dado de sensor disponível</div>
            )}
        </div>
    );
}
