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

function Sidebar({
    isCollapsed,
    onToggle,
    events,
    allEvents,
    filteredAllEvents,
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
    addressToClientMap,
}) {
    const formatPayload = useCallback((payload) => {
        if (!payload) return 'N/A';
        try {
            const parsed = JSON.parse(payload);
            return JSON.stringify(parsed, null, 2);
        } catch {
            return payload.length > 200 ? payload.substring(0, 200) + '...' : payload;
        }
    }, []);

    const resolvePeer = useCallback((event) => {
        if (event.peerId && !/^\/?[\d.:]+$/.test(event.peerId)) {
            return event.peerId;
        }
        
        if (event.remoteAddress && addressToClientMap) {
            const normalizedAddr = normalizeAddress(event.remoteAddress);
            const clientId = addressToClientMap.get(normalizedAddr);
            if (clientId) return clientId;
            return normalizedAddr || event.remoteAddress;
        }
        
        return event.peerId || '?';
    }, [addressToClientMap]);

    const isEventActive = useCallback((localIndex) => {
        const globalIndex = currentPage * pageSize + localIndex;
        return globalIndex === currentIndex;
    }, [currentIndex, currentPage, pageSize]);

    const displayedCount = hasActiveFilters ? filteredAllEvents.length : allEvents.length;
    const totalCount = allEvents.length;

    return (
        <div className="sidebar-wrapper">
            <button 
                className={`sidebar-toggle-handle ${isCollapsed ? 'collapsed' : ''}`}
                onClick={onToggle}
                title={isCollapsed ? 'Expandir sidebar' : 'Recolher sidebar'}
            >
                <ChevronRight className={`toggle-icon ${isCollapsed ? 'collapsed' : ''}`} />
            </button>

            <div className={`sidebar ${isCollapsed ? 'collapsed' : ''}`}>
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
