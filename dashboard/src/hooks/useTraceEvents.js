import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import TraceWorker from '../workers/traceWorker.js?worker';

const DEBUG = false;
const MAX_STORED_EVENTS = 1000;
const PAGE_SIZE = 100;

function formatTimestamp(ts) {
    const date = new Date(ts);
    const h = String(date.getHours()).padStart(2, '0');
    const m = String(date.getMinutes()).padStart(2, '0');
    const s = String(date.getSeconds()).padStart(2, '0');
    const ms = String(date.getMilliseconds()).padStart(3, '0');
    return `${h}:${m}:${s}.${ms}`;
}

export function useTraceEvents() {
    const [allEvents, setAllEvents] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [connected, setConnected] = useState(false);
    const workerRef = useRef(null);

    useEffect(() => {
        const worker = new TraceWorker();
        workerRef.current = worker;

        worker.onmessage = (event) => {
            const { type } = event.data;

            switch (type) {
                case 'status':
                    setConnected(event.data.connected);
                    break;

                case 'events':
                    const newEvents = event.data.events;

                    setAllEvents((prev) => {
                        const existingIds = new Set(prev.map((e) => e.traceId).filter(Boolean));
                        const uniqueNewEvents = newEvents.filter(
                            (e) => e.traceId && !existingIds.has(e.traceId)
                        );

                        if (DEBUG) {
                            for (const traceEvent of uniqueNewEvents) {
                                const dir = traceEvent.direction === 'RECEIVE' ? '<-' : '->';
                                const ts = formatTimestamp(traceEvent.timestamp);
                                const seq = traceEvent.sequenceNumber ?? 'N/A';
                                console.log(`[TraceEvent] ${ts} seq=${seq} | ${traceEvent.componentType} ${dir} ${traceEvent.peerId} | ${traceEvent.messageType} | ${traceEvent.protocol}`);
                            }
                        }

                        const duplicateCount = newEvents.length - uniqueNewEvents.length;
                        if (DEBUG && duplicateCount > 0) {
                            console.log(`[TraceEvents] Filtered ${duplicateCount} duplicate events`);
                        }

                        if (uniqueNewEvents.length === 0) {
                            return prev;
                        }

                        let updated = [...prev, ...uniqueNewEvents];

                        updated.sort((a, b) => {
                            const tsDiff = a.timestamp - b.timestamp;
                            if (tsDiff !== 0) return tsDiff;

                            const compA = a.componentId ?? a.componentType ?? '';
                            const compB = b.componentId ?? b.componentType ?? '';
                            const compDiff = compA.localeCompare(compB);
                            if (compDiff !== 0) return compDiff;

                            const seqA = a.sequenceNumber ?? 0;
                            const seqB = b.sequenceNumber ?? 0;
                            return seqA - seqB;
                        });

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

        worker.postMessage({ type: 'connect' });

        return () => {
            worker.postMessage({ type: 'disconnect' });
            worker.terminate();
        };
    }, []);

    const totalPages = useMemo(() => {
        return Math.max(1, Math.ceil(allEvents.length / PAGE_SIZE));
    }, [allEvents.length]);

    const events = useMemo(() => {
        const start = currentPage * PAGE_SIZE;
        const end = start + PAGE_SIZE;
        return allEvents.slice(start, end);
    }, [allEvents, currentPage]);

    const bufferFull = allEvents.length >= MAX_STORED_EVENTS;

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
        if (workerRef.current) {
            workerRef.current.postMessage({ type: 'clear' });
        }
    }, []);

    return {
        events,
        allEvents,
        connected,
        bufferFull,
        clearEvents,
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
