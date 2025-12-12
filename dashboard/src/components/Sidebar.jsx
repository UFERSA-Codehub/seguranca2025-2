import { memo, useCallback, useMemo } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { normalizeAddress } from '@/contexts/TraceContext';
import {
    ChevronsLeft,
    ChevronLeft,
    ChevronRight,
    ChevronsRight,
} from 'lucide-react';

/**
 * Collapsible sidebar showing trace events list with pagination.
 * Slides in/out from the right side of the viewport.
 * 
 * Now shows correct counts:
 * - When filters active: shows filteredAllEvents.length
 * - When no filters: shows allEvents.length
 * - Pagination reflects filtered list
 */
function Sidebar({
    isCollapsed,
    onToggle,
    events,              // Paginated, filtered events (displayed in list)
    allEvents,           // All stored events (for "total" reference)
    filteredAllEvents,   // All filtered events (for count when filters active)
    connected,
    bufferFull,
    currentPage,
    totalPages,
    pageSize,
    firstPage,
    prevPage,
    nextPage,
    lastPage,
    currentEvent,
    onEventClick,
    currentIndex,
    hasActiveFilters,
    addressToClientMap,  // Map of IP:port → client ID for resolving addresses
}) {
    // Formata o payload para exibição
    const formatPayload = useCallback((payload) => {
        if (!payload) return 'N/A';
        try {
            const parsed = JSON.parse(payload);
            return JSON.stringify(parsed, null, 2);
        } catch {
            return payload.length > 200 ? payload.substring(0, 200) + '...' : payload;
        }
    }, []);

    // Resolve peer for display: prefer peerId, fallback to resolved remoteAddress
    const resolvePeer = useCallback((event) => {
        // If peerId is already a logical name (not an IP), use it
        if (event.peerId && !/^\/?[\d.:]+$/.test(event.peerId)) {
            return event.peerId;
        }
        
        // Try to resolve remoteAddress to a client ID
        if (event.remoteAddress && addressToClientMap) {
            const normalizedAddr = normalizeAddress(event.remoteAddress);
            const clientId = addressToClientMap.get(normalizedAddr);
            if (clientId) return clientId;
            // Return normalized address if no mapping found
            return normalizedAddr || event.remoteAddress;
        }
        
        // Fallback to peerId or ?
        return event.peerId || '?';
    }, [addressToClientMap]);

    // Verifica se o evento está ativo (baseado no índice global)
    const isEventActive = useCallback((localIndex) => {
        const globalIndex = currentPage * pageSize + localIndex;
        return globalIndex === currentIndex;
    }, [currentIndex, currentPage, pageSize]);

    // Determine counts to display
    const displayedCount = hasActiveFilters ? filteredAllEvents.length : allEvents.length;
    const totalCount = allEvents.length;

    return (
        <div className="sidebar-wrapper">
            {/* Toggle Handle - OUTSIDE sidebar for visibility when collapsed */}
            <button 
                className={`sidebar-toggle-handle ${isCollapsed ? 'collapsed' : ''}`}
                onClick={onToggle}
                title={isCollapsed ? 'Expandir sidebar' : 'Recolher sidebar'}
            >
                <ChevronRight className={`toggle-icon ${isCollapsed ? 'collapsed' : ''}`} />
            </button>

            <div className={`sidebar ${isCollapsed ? 'collapsed' : ''}`}>
            {/* Header */}
            <div className="sidebar-header">
                <h2>Eventos de Trace</h2>
                <div className="status">
                    <span className={`dot ${connected ? 'connected' : 'disconnected'}`} />
                    {connected ? 'Conectado' : 'Desconectado'}
                </div>
                {bufferFull && (
                    <Badge variant="secondary" className="bg-amber-500 text-slate-900 text-[10px] font-semibold">
                        Buffer Cheio
                    </Badge>
                )}
            </div>

            {/* Pagination - always visible */}
            <div className="pagination">
                <Button
                    variant="secondary"
                    size="icon"
                    onClick={firstPage}
                    disabled={currentPage === 0}
                    title="Primeira página"
                    className="h-7 w-7 bg-slate-700 hover:bg-slate-600 border-none"
                >
                    <ChevronsLeft className="h-3.5 w-3.5" />
                </Button>
                <Button
                    variant="secondary"
                    size="icon"
                    onClick={prevPage}
                    disabled={currentPage === 0}
                    title="Página anterior"
                    className="h-7 w-7 bg-slate-700 hover:bg-slate-600 border-none"
                >
                    <ChevronLeft className="h-3.5 w-3.5" />
                </Button>
                <span className="page-info">
                    Página {currentPage + 1} / {totalPages}
                    <span className="event-count">
                        {hasActiveFilters 
                            ? `(${displayedCount} de ${totalCount})`
                            : `(${displayedCount} eventos)`
                        }
                    </span>
                </span>
                <Button
                    variant="secondary"
                    size="icon"
                    onClick={nextPage}
                    disabled={currentPage >= totalPages - 1}
                    title="Próxima página"
                    className="h-7 w-7 bg-slate-700 hover:bg-slate-600 border-none"
                >
                    <ChevronRight className="h-3.5 w-3.5" />
                </Button>
                <Button
                    variant="secondary"
                    size="icon"
                    onClick={lastPage}
                    disabled={currentPage >= totalPages - 1}
                    title="Última página"
                    className="h-7 w-7 bg-slate-700 hover:bg-slate-600 border-none"
                >
                    <ChevronsRight className="h-3.5 w-3.5" />
                </Button>
            </div>

            {/* Events List - now shows 'events' which is already paginated from filtered list */}
            <div className="events-list">
                {events.map((event, idx) => (
                    <div
                        key={event.traceId || idx}
                        className={`event-item ${isEventActive(idx) ? 'active' : ''}`}
                        onClick={() => onEventClick(event, idx)}
                    >
                        <div className="event-header">
                            <span className="event-flow">
                                {event.componentType || event.componentId}
                                {event.direction === 'RECEIVE' ? ' ← ' : ' → '}
                                {resolvePeer(event)}
                            </span>
                            <span className="message-type">{event.messageType}</span>
                        </div>
                        <div className="event-meta">
                            <span>{event.protocol}</span>
                        </div>
                    </div>
                ))}
                {events.length === 0 && !hasActiveFilters && (
                    <div className="no-events">Nenhum evento ainda. Inicie o sistema com tracing habilitado.</div>
                )}
                {events.length === 0 && hasActiveFilters && (
                    <div className="no-events">Nenhum evento corresponde aos filtros atuais.</div>
                )}
            </div>

            {/* Payload Panel */}
                {currentEvent && (
                    <div className="payload-panel">
                        <h3>Detalhes do Payload</h3>
                        <div className="payload-section">
                            <h4>Criptografado</h4>
                            <pre>{formatPayload(currentEvent.encryptedPayload)}</pre>
                        </div>
                        <div className="payload-section">
                            <h4>Descriptografado</h4>
                            <pre>{formatPayload(currentEvent.decryptedPayload)}</pre>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

export default memo(Sidebar);
