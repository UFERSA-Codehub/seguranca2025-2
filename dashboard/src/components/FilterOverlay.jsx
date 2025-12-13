import { memo, useEffect, useState, useCallback, useRef } from 'react';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
function useMaxItemsPerColumn() {
    const [maxItems, setMaxItems] = useState(10);
    useEffect(() => {
        const calculate = () => {
            const ITEM_HEIGHT = 36;
            const HEADER_HEIGHT = 56;
            const VERTICAL_PADDING = 160; // Increased for hint text
            const MAX_ITEMS = 10; // Hard limit to prevent scroll
            const available = window.innerHeight - HEADER_HEIGHT - VERTICAL_PADDING;
            const calculated = Math.max(5, Math.floor(available / ITEM_HEIGHT));
            setMaxItems(Math.min(calculated, MAX_ITEMS));
        };
        calculate();
        window.addEventListener('resize', calculate);
        return () => window.removeEventListener('resize', calculate);
    }, []);
    return maxItems;
}
function splitIntoColumns(items, maxPerColumn) {
    const columns = [];
    for (let i = 0; i < items.length; i += maxPerColumn) {
        columns.push(items.slice(i, i + maxPerColumn));
    }
    return columns.length > 0 ? columns : [[]];
}
function FilterOverlay({
    isOpen,
    onClose,
    entities,
    messageTypes,
    selectedEntities,
    selectedMessageTypes,
    excludedEntities,
    excludedMessageTypes,
    onToggleEntity,
    onToggleMessageType,
    onExcludeEntity,
    onExcludeMessageType,
    onClearAll,
    isSidebarCollapsed,
}) {
    const overlayRef = useRef(null);
    const maxItemsPerColumn = useMaxItemsPerColumn();
    const entityColumns = splitIntoColumns(entities, maxItemsPerColumn);
    const messageTypeColumns = splitIntoColumns(messageTypes, maxItemsPerColumn);
    const hasActiveFilters = selectedEntities.size > 0 || selectedMessageTypes.size > 0 || excludedEntities.size > 0 || excludedMessageTypes.size > 0;
    const handleClickOutside = useCallback((e) => {
        if (overlayRef.current && !overlayRef.current.contains(e.target)) {
            const toolbar = document.querySelector('.floating-toolbar');
            if (toolbar && toolbar.contains(e.target)) return;
            onClose();
        }
    }, [onClose]);
    const handleKeyDown = useCallback((e) => {
        if (e.key === 'Escape') {
            onClose();
        }
    }, [onClose]);
    useEffect(() => {
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            document.addEventListener('keydown', handleKeyDown);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen, handleClickOutside, handleKeyDown]);
    if (!isOpen) return null;
    return (
        <div className={`filter-overlay ${isSidebarCollapsed ? 'sidebar-collapsed' : ''}`} ref={overlayRef}>
            {/* Header */}
            <div className="filter-overlay-header">
                <h3 className="text-sm font-semibold text-slate-100">Filtros</h3>
                <div className="flex items-center gap-2">
                    {hasActiveFilters && (
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={onClearAll}
                            className="h-7 text-xs text-indigo-400 hover:text-indigo-300 hover:bg-slate-800"
                        >
                            Limpar tudo
                        </Button>
                    )}
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={onClose}
                        className="h-7 w-7 hover:bg-slate-700 text-slate-400"
                    >
                        <X className="h-4 w-4" />
                    </Button>
                </div>
            </div>
            <Separator className="bg-slate-700" />
            {/* Content */}
            <div className="filter-overlay-content">
                {/* Entities Section */}
                <div className="filter-section">
                    <h4 className="filter-section-title">Entidades</h4>
                    <p className="filter-hint">Clique: incluir | Clique direito: excluir</p>
                    <div className="filter-columns">
                        {entityColumns.map((column, colIdx) => (
                            <div key={`entity-col-${colIdx}`} className="filter-column">
                                {column.map((entity) => {
                                    const isIncluded = selectedEntities.has(entity);
                                    const isExcluded = excludedEntities.has(entity);
                                    return (
                                        <Button
                                            key={entity}
                                            variant={isIncluded || isExcluded ? "default" : "outline"}
                                            size="sm"
                                            onClick={() => onToggleEntity(entity)}
                                            onContextMenu={(e) => {
                                                e.preventDefault();
                                                onExcludeEntity(entity);
                                            }}
                                            className={`filter-item ${
                                                isExcluded
                                                    ? 'bg-red-600 hover:bg-red-500 border-red-600'
                                                    : isIncluded
                                                    ? 'bg-indigo-600 hover:bg-indigo-500 border-indigo-600'
                                                    : 'bg-slate-800 hover:bg-slate-700 border-slate-600 text-slate-200'
                                            }`}
                                        >
                                            {entity}
                                        </Button>
                                    );
                                })}
                            </div>
                        ))}
                        {entities.length === 0 && (
                            <span className="text-xs text-slate-500 italic py-2">
                                Nenhuma entidade disponível
                            </span>
                        )}
                    </div>
                </div>
                <Separator orientation="vertical" className="bg-slate-700 h-auto" />
                {/* Message Types Section */}
                <div className="filter-section">
                    <h4 className="filter-section-title">Tipos de Mensagem</h4>
                    <p className="filter-hint">Clique: incluir | Clique direito: excluir</p>
                    <div className="filter-columns">
                        {messageTypeColumns.map((column, colIdx) => (
                            <div key={`msgtype-col-${colIdx}`} className="filter-column">
                                {column.map((type) => {
                                    const isIncluded = selectedMessageTypes.has(type);
                                    const isExcluded = excludedMessageTypes.has(type);
                                    return (
                                        <Button
                                            key={type}
                                            variant={isIncluded || isExcluded ? "default" : "outline"}
                                            size="sm"
                                            onClick={() => onToggleMessageType(type)}
                                            onContextMenu={(e) => {
                                                e.preventDefault();
                                                onExcludeMessageType(type);
                                            }}
                                            className={`filter-item ${
                                                isExcluded
                                                    ? 'bg-red-600 hover:bg-red-500 border-red-600'
                                                    : isIncluded
                                                    ? 'bg-indigo-600 hover:bg-indigo-500 border-indigo-600'
                                                    : 'bg-slate-800 hover:bg-slate-700 border-slate-600 text-slate-200'
                                            }`}
                                        >
                                            {type}
                                        </Button>
                                    );
                                })}
                            </div>
                        ))}
                        {messageTypes.length === 0 && (
                            <span className="text-xs text-slate-500 italic py-2">
                                Nenhum tipo de mensagem disponível
                            </span>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
export default memo(FilterOverlay);
