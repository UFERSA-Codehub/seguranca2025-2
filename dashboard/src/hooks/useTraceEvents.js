import { useState, useEffect, useCallback, useRef } from 'react';

const WS_URL = `ws://${window.location.hostname}:6001`;
const MAX_EVENTS = 100;

export function useTraceEvents() {
    const [events, setEvents] = useState([]);
    const [connected, setConnected] = useState(false);
    const [bufferFull, setBufferFull] = useState(false);
    const wsRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);

    const connect = useCallback(() => {
        if (wsRef.current?.readyState === WebSocket.OPEN) return;

        const ws = new WebSocket(WS_URL);

        ws.onopen = () => {
            setConnected(true);
        };

        ws.onmessage = (event) => {
            try {
                const traceEvent = JSON.parse(event.data);
                setEvents((prev) => {
                    if (prev.length >= MAX_EVENTS) {
                        setBufferFull(true);
                        return prev;
                    }
                    const updated = [...prev, traceEvent];
                    // Sort by timestamp descending (newest first)
                    updated.sort((a, b) => b.timestamp - a.timestamp);
                    if (updated.length >= MAX_EVENTS) {
                        setBufferFull(true);
                    }
                    return updated;
                });
            } catch (e) {
                console.error('Failed to parse trace event:', e);
            }
        };

        ws.onclose = () => {
            setConnected(false);
            reconnectTimeoutRef.current = setTimeout(connect, 3000);
        };

        ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };

        wsRef.current = ws;
    }, []);

    useEffect(() => {
        connect();

        return () => {
            if (reconnectTimeoutRef.current) {
                clearTimeout(reconnectTimeoutRef.current);
            }
            if (wsRef.current) {
                wsRef.current.close();
            }
        };
    }, [connect]);

    const clearEvents = useCallback(() => {
        setEvents([]);
        setBufferFull(false);
    }, []);

    return { events, connected, bufferFull, clearEvents };
}
