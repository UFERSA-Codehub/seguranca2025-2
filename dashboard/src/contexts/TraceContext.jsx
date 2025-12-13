import { createContext, useContext, useState, useEffect, useCallback, useRef, useMemo, startTransition } from 'react';
import TraceWorker from '../workers/traceWorker.js?worker';

const DEBUG = false;
const MAX_STORED_EVENTS = 1000;
const PAGE_SIZE = 10;

const DEFERRED_FLUSH_INTERVAL_MS = 2000;

const TraceContext = createContext(null);

function formatTimestamp(ts) {
    const date = new Date(ts);
    const h = String(date.getHours()).padStart(2, '0');
    const m = String(date.getMinutes()).padStart(2, '0');
    const s = String(date.getSeconds()).padStart(2, '0');
    const ms = String(date.getMilliseconds()).padStart(3, '0');
    return `${h}:${m}:${s}.${ms}`;
}

function sortEvents(events) {
    return events.sort((a, b) => {
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
}

function mergeEvents(existing, incoming, maxSize) {
    const existingIds = new Set(existing.map((e) => e.traceId).filter(Boolean));
    const uniqueNew = incoming.filter((e) => e.traceId && !existingIds.has(e.traceId));
    
    if (uniqueNew.length === 0) {
        return existing;
    }
    
    let updated = [...existing, ...uniqueNew];
    updated = sortEvents(updated);
    
    if (updated.length > maxSize) {
        updated = updated.slice(0, maxSize);
    }
    
    return updated;
}

function isExternalClient(id) {
    if (!id) return false;
    return /^(SENSOR|CLIENT|CLI|MALICIOUS)_/.test(id) || id === 'BROWSER';
}

function isIpAddress(id) {
    if (!id) return false;
    return /^\/?(\d{1,3}\.){3}\d{1,3}:\d+$/.test(id);
}

export function normalizeAddress(addr) {
    if (!addr) return null;
    let normalized = addr;
    
    if (normalized.startsWith('/')) {
        normalized = normalized.slice(1);
    }
    
    const slashIndex = normalized.indexOf('/');
    if (slashIndex > 0) {
        normalized = normalized.slice(slashIndex + 1);
    }
    
    if (normalized.startsWith('0:0:0:0:0:0:0:0:')) {
        const port = normalized.split(':').pop();
        normalized = `127.0.0.1:${port}`;
    }
    
    if (normalized.startsWith('[::]:')) {
        const port = normalized.slice(5);
        normalized = `127.0.0.1:${port}`;
    }
    
    return normalized;
}

function normalizeForTopology(id, addressToClientMap) {
    if (!id) return null;
    if (id.startsWith('RP-')) return 'REVERSE_PROXY';
    
    if (isIpAddress(id)) {
        const normalizedAddr = normalizeAddress(id);
        const clientId = addressToClientMap?.get(normalizedAddr);
        if (clientId) return clientId;
        return normalizedAddr;
    }
    
    const normalizations = {
        'ReverseProxy': 'REVERSE_PROXY',
        'PacketFilter': 'PACKET_FILTER',
        'Datacenter': 'DATACENTER',
        'Edge': 'EDGE',
        'Discovery': 'DISCOVERY',
        'AuthServer': 'AUTH',
        'Auth': 'AUTH',
        'IDS': 'IDS',
        'IdsServer': 'IDS',
    };
    
    return normalizations[id] || id;
}

function getEventEntities(event, addressToClientMap) {
    const entities = new Set();
    const component = normalizeForTopology(event.componentType, addressToClientMap);
    const peer = normalizeForTopology(event.peerId, addressToClientMap);
    if (component) entities.add(component);
    if (peer) entities.add(peer);
    return entities;
}

export function TraceProvider({ children }) {
    const [allEvents, setAllEvents] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [connected, setConnected] = useState(false);
    const [hasReceivedInitialData, setHasReceivedInitialData] = useState(false);
    
    const [selectedEntities, setSelectedEntities] = useState(new Set());
    const [excludedEntities, setExcludedEntities] = useState(new Set());
    const [selectedMessageTypes, setSelectedMessageTypes] = useState(new Set());
    const [excludedMessageTypes, setExcludedMessageTypes] = useState(new Set());
    
    const [isPaused, setIsPaused] = useState(false);
    const [bufferedEventCount, setBufferedEventCount] = useState(0);
    const isPausedRef = useRef(false);
    const hiddenBufferRef = useRef([]);
    
    const [savedViewport, setSavedViewport] = useState(null);
    
    const pendingEventsRef = useRef([]);
    const isInitialBatchRef = useRef(true);
    
    const workerRef = useRef(null);

    useEffect(() => {
        isPausedRef.current = isPaused;
    }, [isPaused]);

    const flushPendingEvents = useCallback(() => {
        if (pendingEventsRef.current.length === 0) return;
        
        const eventsToFlush = pendingEventsRef.current;
        pendingEventsRef.current = [];
        
        if (DEBUG) {
            console.log(`[TraceContext] Flushing ${eventsToFlush.length} pending events to state`);
        }
        
        startTransition(() => {
            setAllEvents((prev) => mergeEvents(prev, eventsToFlush, MAX_STORED_EVENTS));
        });
    }, []);

    useEffect(() => {
        const intervalId = setInterval(flushPendingEvents, DEFERRED_FLUSH_INTERVAL_MS);
        return () => clearInterval(intervalId);
    }, [flushPendingEvents]);

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
                    
                    if (!hasReceivedInitialData) {
                        setHasReceivedInitialData(true);
                    }

                    if (isPausedRef.current) {
                        hiddenBufferRef.current = [...hiddenBufferRef.current, ...newEvents];
                        
                        if (hiddenBufferRef.current.length > MAX_STORED_EVENTS) {
                            hiddenBufferRef.current = hiddenBufferRef.current.slice(-MAX_STORED_EVENTS);
                        }
                        
                        setBufferedEventCount(hiddenBufferRef.current.length);
                        
                        if (DEBUG) {
                            console.log(`[TraceContext] Buffered ${newEvents.length} events (paused), buffer size: ${hiddenBufferRef.current.length}`);
                        }
                        return;
                    }

                    if (DEBUG) {
                        for (const traceEvent of newEvents) {
                            const dir = traceEvent.direction === 'RECEIVE' ? '<-' : '->';
                            const ts = formatTimestamp(traceEvent.timestamp);
                            const seq = traceEvent.sequenceNumber ?? 'N/A';
                            console.log(`[TraceEvent] ${ts} seq=${seq} | ${traceEvent.componentType} ${dir} ${traceEvent.peerId} | ${traceEvent.messageType} | ${traceEvent.protocol}`);
                        }
                    }

                    if (isInitialBatchRef.current) {
                        isInitialBatchRef.current = false;
                        setAllEvents((prev) => mergeEvents(prev, newEvents, MAX_STORED_EVENTS));
                        return;
                    }
                    
                    pendingEventsRef.current = [...pendingEventsRef.current, ...newEvents];
                    
                    if (pendingEventsRef.current.length > MAX_STORED_EVENTS) {
                        pendingEventsRef.current = pendingEventsRef.current.slice(-MAX_STORED_EVENTS);
                    }
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
    }, [hasReceivedInitialData]);

    const pauseUpdates = useCallback(() => {
        if (DEBUG) {
            console.log('[TraceContext] Pausing event updates');
        }
        setIsPaused(true);
        isPausedRef.current = true;
        hiddenBufferRef.current = [];
        setBufferedEventCount(0);
    }, []);

    const resumeUpdates = useCallback(() => {
        if (DEBUG) {
            console.log(`[TraceContext] Resuming event updates, merging ${hiddenBufferRef.current.length} buffered events`);
        }
        
        const buffered = hiddenBufferRef.current;
        hiddenBufferRef.current = [];
        setBufferedEventCount(0);
        
        if (buffered.length > 0) {
            startTransition(() => {
                setAllEvents((prev) => mergeEvents(prev, buffered, MAX_STORED_EVENTS));
            });
        }
        
        setIsPaused(false);
        isPausedRef.current = false;
    }, []);

    const addressToClientMap = useMemo(() => {
        const map = new Map();
        
        for (const event of allEvents) {
            if (isExternalClient(event.peerId) && event.remoteAddress && !isExternalClient(event.componentType)) {
                const addr = normalizeAddress(event.remoteAddress);
                if (addr) map.set(addr, event.peerId);
            }
            
            if (isExternalClient(event.componentType) && event.localAddress) {
                const addr = normalizeAddress(event.localAddress);
                if (addr && !map.has(addr)) {
                    map.set(addr, event.componentType);
                }
            }
            
            if (isExternalClient(event.componentType) && event.remoteAddress) {
                const addr = normalizeAddress(event.remoteAddress);
                if (addr && !map.has(addr)) {
                    map.set(addr, event.componentType);
                }
            }
        }
        return map;
    }, [allEvents]);

    const filteredAllEvents = useMemo(() => {
        const hasFilters = selectedEntities.size > 0 || excludedEntities.size > 0 || 
                          selectedMessageTypes.size > 0 || excludedMessageTypes.size > 0;
        
        if (!hasFilters) {
            return allEvents;
        }
        
        return allEvents.filter(event => {
            const entities = getEventEntities(event, addressToClientMap);
            
            if (excludedEntities.size > 0) {
                const hasExcludedEntity = [...entities].some(e => excludedEntities.has(e));
                if (hasExcludedEntity) return false;
            }
            if (excludedMessageTypes.size > 0) {
                if (excludedMessageTypes.has(event.messageType)) return false;
            }
            
            if (selectedEntities.size > 0) {
                const matchesEntity = [...entities].some(e => selectedEntities.has(e));
                if (!matchesEntity) return false;
            }
            if (selectedMessageTypes.size > 0) {
                if (!selectedMessageTypes.has(event.messageType)) return false;
            }
            
            return true;
        });
    }, [allEvents, selectedEntities, excludedEntities, selectedMessageTypes, excludedMessageTypes, addressToClientMap]);

    const totalPages = useMemo(() => {
        return Math.max(1, Math.ceil(filteredAllEvents.length / PAGE_SIZE));
    }, [filteredAllEvents.length]);

    const events = useMemo(() => {
        const start = currentPage * PAGE_SIZE;
        const end = start + PAGE_SIZE;
        return filteredAllEvents.slice(start, end);
    }, [filteredAllEvents, currentPage]);

    useEffect(() => {
        setCurrentPage(0);
    }, [selectedEntities, excludedEntities, selectedMessageTypes, excludedMessageTypes]);

    const bufferFull = allEvents.length >= MAX_STORED_EVENTS;
    const hasActiveFilters = selectedEntities.size > 0 || excludedEntities.size > 0 || 
                            selectedMessageTypes.size > 0 || excludedMessageTypes.size > 0;
    const activeFilterCount = selectedEntities.size + excludedEntities.size + 
                             selectedMessageTypes.size + excludedMessageTypes.size;

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
        hiddenBufferRef.current = [];
        pendingEventsRef.current = [];
        setBufferedEventCount(0);
        isInitialBatchRef.current = true;
        setHasReceivedInitialData(false);
        if (workerRef.current) {
            workerRef.current.postMessage({ type: 'clear' });
        }
    }, []);

    const toggleEntity = useCallback((entity) => {
        setSelectedEntities(prev => {
            const next = new Set(prev);
            if (next.has(entity)) {
                next.delete(entity);
            } else {
                next.add(entity);
            }
            return next;
        });
        setExcludedEntities(prev => {
            const next = new Set(prev);
            next.delete(entity);
            return next;
        });
    }, []);

    const toggleMessageType = useCallback((type) => {
        setSelectedMessageTypes(prev => {
            const next = new Set(prev);
            if (next.has(type)) {
                next.delete(type);
            } else {
                next.add(type);
            }
            return next;
        });
        setExcludedMessageTypes(prev => {
            const next = new Set(prev);
            next.delete(type);
            return next;
        });
    }, []);

    const excludeEntity = useCallback((entity) => {
        setExcludedEntities(prev => {
            const next = new Set(prev);
            if (next.has(entity)) {
                next.delete(entity);
            } else {
                next.add(entity);
            }
            return next;
        });
        setSelectedEntities(prev => {
            const next = new Set(prev);
            next.delete(entity);
            return next;
        });
    }, []);

    const excludeMessageType = useCallback((type) => {
        setExcludedMessageTypes(prev => {
            const next = new Set(prev);
            if (next.has(type)) {
                next.delete(type);
            } else {
                next.add(type);
            }
            return next;
        });
        setSelectedMessageTypes(prev => {
            const next = new Set(prev);
            next.delete(type);
            return next;
        });
    }, []);

    const clearAllFilters = useCallback(() => {
        setSelectedEntities(new Set());
        setExcludedEntities(new Set());
        setSelectedMessageTypes(new Set());
        setExcludedMessageTypes(new Set());
    }, []);

    const value = {
        events,
        allEvents,
        filteredAllEvents,
        connected,
        hasReceivedInitialData,
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
        
        selectedEntities,
        excludedEntities,
        selectedMessageTypes,
        excludedMessageTypes,
        hasActiveFilters,
        activeFilterCount,
        toggleEntity,
        toggleMessageType,
        excludeEntity,
        excludeMessageType,
        clearAllFilters,
        
        addressToClientMap,
        
        isPaused,
        pauseUpdates,
        resumeUpdates,
        bufferedEventCount,
        
        savedViewport,
        setSavedViewport,
    };

    return (
        <TraceContext.Provider value={value}>
            {children}
        </TraceContext.Provider>
    );
}

export function useTrace() {
    const context = useContext(TraceContext);
    if (!context) {
        throw new Error('useTrace must be used within a TraceProvider');
    }
    return context;
}
