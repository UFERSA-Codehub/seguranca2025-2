import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import TraceWorker from '../workers/traceWorker.js?worker';

const DEBUG = false;
const MAX_STORED_EVENTS = 1000; // Limite interno de armazenamento
const PAGE_SIZE = 100; // Eventos exibidos por pagina

// Formata timestamp para leitura humana (HH:MM:SS.mmm)
function formatTimestamp(ts) {
    const date = new Date(ts);
    const h = String(date.getHours()).padStart(2, '0');
    const m = String(date.getMinutes()).padStart(2, '0');
    const s = String(date.getSeconds()).padStart(2, '0');
    const ms = String(date.getMilliseconds()).padStart(3, '0');
    return `${h}:${m}:${s}.${ms}`;
}

export function useTraceEvents() {
    const [allEvents, setAllEvents] = useState([]); // Todos os eventos armazenados
    const [currentPage, setCurrentPage] = useState(0); // Pagina atual (0-indexed)
    const [connected, setConnected] = useState(false);
    const workerRef = useRef(null);

    useEffect(() => {
        // Criar worker
        const worker = new TraceWorker();
        workerRef.current = worker;

        // Handler de mensagens do worker
        worker.onmessage = (event) => {
            const { type } = event.data;

            switch (type) {
                case 'status':
                    setConnected(event.data.connected);
                    break;

                case 'events':
                    const newEvents = event.data.events;

                    setAllEvents((prev) => {
                        // Filtrar duplicatas por traceId
                        const existingIds = new Set(prev.map((e) => e.traceId).filter(Boolean));
                        const uniqueNewEvents = newEvents.filter(
                            (e) => e.traceId && !existingIds.has(e.traceId)
                        );

                        // Log apenas eventos que sao realmente novos
                        if (DEBUG) {
                            for (const traceEvent of uniqueNewEvents) {
                                const dir = traceEvent.direction === 'RECEIVE' ? '<-' : '->';
                                const ts = formatTimestamp(traceEvent.timestamp);
                                const seq = traceEvent.sequenceNumber ?? 'N/A';
                                console.log(`[TraceEvent] ${ts} seq=${seq} | ${traceEvent.componentType} ${dir} ${traceEvent.peerId} | ${traceEvent.messageType} | ${traceEvent.protocol}`);
                            }
                        }

                        // Log duplicatas filtradas (debug)
                        const duplicateCount = newEvents.length - uniqueNewEvents.length;
                        if (DEBUG && duplicateCount > 0) {
                            console.log(`[TraceEvents] Filtered ${duplicateCount} duplicate events`);
                        }

                        if (uniqueNewEvents.length === 0) {
                            return prev;
                        }

                        // Combinar e ordenar
                        let updated = [...prev, ...uniqueNewEvents];

                        // Ordenar ascending (oldest first) por: timestamp, componentId, sequenceNumber
                        // Isso permite que pagina 0 contenha os eventos mais antigos (para demo)
                        updated.sort((a, b) => {
                            // 1. Timestamp (ascending - oldest first)
                            const tsDiff = a.timestamp - b.timestamp;
                            if (tsDiff !== 0) return tsDiff;

                            // 2. ComponentId (ascending)
                            const compA = a.componentId ?? a.componentType ?? '';
                            const compB = b.componentId ?? b.componentType ?? '';
                            const compDiff = compA.localeCompare(compB);
                            if (compDiff !== 0) return compDiff;

                            // 3. SequenceNumber (ascending)
                            const seqA = a.sequenceNumber ?? 0;
                            const seqB = b.sequenceNumber ?? 0;
                            return seqA - seqB;
                        });

                        // Manter apenas os eventos mais antigos (para demo)
                        // Quando buffer enche, para de aceitar novos
                        if (updated.length > MAX_STORED_EVENTS) {
                            updated = updated.slice(0, MAX_STORED_EVENTS);
                        }

                        return updated;
                    });
                    break;

                default:
                    break;
            }
        };

        // Conectar
        worker.postMessage({ type: 'connect' });

        // Cleanup
        return () => {
            worker.postMessage({ type: 'disconnect' });
            worker.terminate();
        };
    }, []);

    // Calcular total de paginas
    const totalPages = useMemo(() => {
        return Math.max(1, Math.ceil(allEvents.length / PAGE_SIZE));
    }, [allEvents.length]);

    // Eventos da pagina atual
    const events = useMemo(() => {
        const start = currentPage * PAGE_SIZE;
        const end = start + PAGE_SIZE;
        return allEvents.slice(start, end);
    }, [allEvents, currentPage]);

    // Verificar se buffer esta cheio
    const bufferFull = allEvents.length >= MAX_STORED_EVENTS;

    // Navegacao de paginas
    const goToPage = useCallback((page) => {
        setCurrentPage(Math.max(0, Math.min(page, totalPages - 1)));
    }, [totalPages]);

    const nextPage = useCallback(() => {
        setCurrentPage((prev) => Math.min(prev + 1, totalPages - 1));
    }, [totalPages]);

    const prevPage = useCallback(() => {
        setCurrentPage((prev) => Math.max(prev - 1, 0));
    }, []);

    const firstPage = useCallback(() => {
        setCurrentPage(0);
    }, []);

    const lastPage = useCallback(() => {
        setCurrentPage(totalPages - 1);
    }, [totalPages]);

    const clearEvents = useCallback(() => {
        setAllEvents([]);
        setCurrentPage(0);
        // Limpar buffer do worker tambem
        if (workerRef.current) {
            workerRef.current.postMessage({ type: 'clear' });
        }
    }, []);

    return {
        events,           // Eventos da pagina atual (max PAGE_SIZE)
        allEvents,        // Todos os eventos armazenados
        connected,
        bufferFull,
        clearEvents,
        // Paginacao
        currentPage,
        totalPages,
        pageSize: PAGE_SIZE,
        goToPage,
        nextPage,
        prevPage,
        firstPage,
        lastPage,
    };
}
