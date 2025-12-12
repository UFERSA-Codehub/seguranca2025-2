import { createContext, useContext, useState, useEffect, useCallback, useRef, useMemo, startTransition } from 'react';
import TraceWorker from '../workers/traceWorker.js?worker';

const DEBUG = false;
const MAX_STORED_EVENTS = 1000;
const PAGE_SIZE = 10;

// How often to flush pending events to state (ms)
const DEFERRED_FLUSH_INTERVAL_MS = 2000;

const TraceContext = createContext(null);

// Formata timestamp para leitura humana (HH:MM:SS.mmm)
function formatTimestamp(ts) {
    const date = new Date(ts);
    const h = String(date.getHours()).padStart(2, '0');
    const m = String(date.getMinutes()).padStart(2, '0');
    const s = String(date.getSeconds()).padStart(2, '0');
    const ms = String(date.getMilliseconds()).padStart(3, '0');
    return `${h}:${m}:${s}.${ms}`;
}

// Funcao de ordenacao reutilizavel
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

// Merge new events into existing, deduplicating and sorting
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

// Helper to check if an ID is an external client (sensor, client, malicious)
function isExternalClient(id) {
    if (!id) return false;
    return /^(SENSOR|CLIENT|CLI|MALICIOUS)_/.test(id) || id === 'BROWSER';
}

// Helper to check if ID is an IP address
function isIpAddress(id) {
    if (!id) return false;
    return /^\/?(\d{1,3}\.){3}\d{1,3}:\d+$/.test(id);
}

/**
 * Normalize address format for consistent display and mapping.
 * Handles:
 * - Leading slash: /127.0.0.1:45654 → 127.0.0.1:45654
 * - Hostname prefix: localhost/127.0.0.1:3002 → 127.0.0.1:3002
 * - IPv6 localhost: 0:0:0:0:0:0:0:0:34950 → 127.0.0.1:34950
 * - IPv6 with brackets: [::]:34950 → 127.0.0.1:34950
 */
export function normalizeAddress(addr) {
    if (!addr) return null;
    let normalized = addr;
    
    // Remove leading slash
    if (normalized.startsWith('/')) {
        normalized = normalized.slice(1);
    }
    
    // Remove hostname/ prefix (e.g., localhost/127.0.0.1:3002)
    const slashIndex = normalized.indexOf('/');
    if (slashIndex > 0) {
        normalized = normalized.slice(slashIndex + 1);
    }
    
    // Convert IPv6 localhost (0:0:0:0:0:0:0:0:port) to IPv4 (127.0.0.1:port)
    if (normalized.startsWith('0:0:0:0:0:0:0:0:')) {
        const port = normalized.split(':').pop();
        normalized = `127.0.0.1:${port}`;
    }
    
    // Convert IPv6 bracket notation ([::]:port) to IPv4
    if (normalized.startsWith('[::]:')) {
        const port = normalized.slice(5);
        normalized = `127.0.0.1:${port}`;
    }
    
    return normalized;
}

// Normalize ID for topology (remove prefixes, map common names)
function normalizeForTopology(id, addressToClientMap) {
    if (!id) return null;
    if (id.startsWith('RP-')) return 'REVERSE_PROXY';
    
    // If it's an IP address, try to resolve to a client ID
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

// Get entities involved in an event
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
    
    // Filter state - centralized here so pagination works correctly
    const [selectedEntities, setSelectedEntities] = useState(new Set());
    const [excludedEntities, setExcludedEntities] = useState(new Set());
    const [selectedMessageTypes, setSelectedMessageTypes] = useState(new Set());
    const [excludedMessageTypes, setExcludedMessageTypes] = useState(new Set());
    
    // Pause system for playback mode
    const [isPaused, setIsPaused] = useState(false);
    const [bufferedEventCount, setBufferedEventCount] = useState(0);
    const isPausedRef = useRef(false);
    const hiddenBufferRef = useRef([]);
    
    // Topology viewport persistence (survives component unmount)
    const [savedViewport, setSavedViewport] = useState(null);
    
    // Pending events accumulator - events wait here before being flushed to state
    const pendingEventsRef = useRef([]);
    const isInitialBatchRef = useRef(true);
    
    const workerRef = useRef(null);

    // Sync ref with state
    useEffect(() => {
        isPausedRef.current = isPaused;
    }, [isPaused]);

    // Flush pending events to state using startTransition for non-blocking updates
    const flushPendingEvents = useCallback(() => {
        if (pendingEventsRef.current.length === 0) return;
        
        const eventsToFlush = pendingEventsRef.current;
        pendingEventsRef.current = [];
        
        if (DEBUG) {
            console.log(`[TraceContext] Flushing ${eventsToFlush.length} pending events to state`);
        }
        
        // Use startTransition to mark this as a low-priority update
        // This allows user interactions to interrupt if needed
        startTransition(() => {
            setAllEvents((prev) => mergeEvents(prev, eventsToFlush, MAX_STORED_EVENTS));
        });
    }, []);

    // Set up periodic flush of pending events
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
                    
                    // Mark that we've received initial data from server
                    if (!hasReceivedInitialData) {
                        setHasReceivedInitialData(true);
                    }

                    // If paused (playback mode), buffer events instead of updating state
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

                    // First batch (initial history from server) - update immediately
                    // This ensures the topology gets dynamic nodes right away
                    if (isInitialBatchRef.current) {
                        isInitialBatchRef.current = false;
                        setAllEvents((prev) => mergeEvents(prev, newEvents, MAX_STORED_EVENTS));
                        return;
                    }
                    
                    // Subsequent batches - accumulate in pending ref for deferred flush
                    // This prevents constant re-renders during active system usage
                    pendingEventsRef.current = [...pendingEventsRef.current, ...newEvents];
                    
                    // Prevent memory issues if flush is delayed
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

    // Pause event updates (for playback mode)
    const pauseUpdates = useCallback(() => {
        if (DEBUG) {
            console.log('[TraceContext] Pausing event updates');
        }
        setIsPaused(true);
        isPausedRef.current = true;
        hiddenBufferRef.current = [];
        setBufferedEventCount(0);
    }, []);

    // Resume event updates and merge buffered events
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

    // Build address-to-client mapping from ALL events
    // This helps resolve IP:port addresses to logical client IDs
    // Uses both remoteAddress (server perspective) and localAddress (client perspective)
    const addressToClientMap = useMemo(() => {
        const map = new Map();
        
        for (const event of allEvents) {
            // Case 1: Server received from known client - highest priority
            // Server's remoteAddress = Client's localAddress
            if (isExternalClient(event.peerId) && event.remoteAddress && !isExternalClient(event.componentType)) {
                const addr = normalizeAddress(event.remoteAddress);
                if (addr) map.set(addr, event.peerId);
            }
            
            // Case 2: Client's own event - use localAddress to map the client's ephemeral port
            // This is the key addition: when SENSOR_001 sends, its localAddress is 127.0.0.1:54360
            // Later, when PACKET_FILTER receives from 127.0.0.1:54360, we can resolve it
            if (isExternalClient(event.componentType) && event.localAddress) {
                const addr = normalizeAddress(event.localAddress);
                if (addr && !map.has(addr)) {
                    map.set(addr, event.componentType);
                }
            }
            
            // Case 3: Fallback - use remoteAddress from client events (less reliable, server port)
            if (isExternalClient(event.componentType) && event.remoteAddress) {
                const addr = normalizeAddress(event.remoteAddress);
                if (addr && !map.has(addr)) {
                    map.set(addr, event.componentType);
                }
            }
        }
        return map;
    }, [allEvents]);

    // Filter all events BEFORE pagination
    // This is the key fix - filtering must happen before slicing for pagination
    const filteredAllEvents = useMemo(() => {
        const hasFilters = selectedEntities.size > 0 || excludedEntities.size > 0 || 
                          selectedMessageTypes.size > 0 || excludedMessageTypes.size > 0;
        
        if (!hasFilters) {
            return allEvents;
        }
        
        return allEvents.filter(event => {
            const entities = getEventEntities(event, addressToClientMap);
            
            // Check exclusions first
            if (excludedEntities.size > 0) {
                const hasExcludedEntity = [...entities].some(e => excludedEntities.has(e));
                if (hasExcludedEntity) return false;
            }
            if (excludedMessageTypes.size > 0) {
                if (excludedMessageTypes.has(event.messageType)) return false;
            }
            
            // Then check inclusions
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

    // Calculate total pages from FILTERED events
    const totalPages = useMemo(() => {
        return Math.max(1, Math.ceil(filteredAllEvents.length / PAGE_SIZE));
    }, [filteredAllEvents.length]);

    // Paginate from FILTERED events
    const events = useMemo(() => {
        const start = currentPage * PAGE_SIZE;
        const end = start + PAGE_SIZE;
        return filteredAllEvents.slice(start, end);
    }, [filteredAllEvents, currentPage]);

    // Reset to page 0 when filters change (to avoid being on invalid page)
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

    // Filter manipulation functions
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
        // Remove from excluded if adding to selected
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
        // Remove from excluded if adding to selected
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
        // Remove from selected if adding to excluded
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
        // Remove from selected if adding to excluded
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
        // Events
        events,                    // Paginated, filtered events (for display in list)
        allEvents,                 // All stored events (unfiltered, for building dynamic nodes)
        filteredAllEvents,         // All filtered events (for playback, counts)
        connected,
        hasReceivedInitialData,
        bufferFull,
        clearEvents,
        
        // Pagination
        currentPage,
        totalPages,
        pageSize: PAGE_SIZE,
        goToPage,
        nextPage,
        prevPage,
        firstPage,
        lastPage,
        
        // Filters
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
        
        // Address mapping (for topology)
        addressToClientMap,
        
        // Playback pause/resume
        isPaused,
        pauseUpdates,
        resumeUpdates,
        bufferedEventCount,
        
        // Topology viewport persistence
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
