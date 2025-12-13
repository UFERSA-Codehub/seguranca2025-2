import { memo } from 'react';
import { Filter, Trash2, Maximize } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

function FloatingToolbar({
    onFilterClick,
    onClearClick,
    onResetView,
    isSidebarCollapsed,
    hasActiveFilters,
    activeFilterCount,
    isFilterOpen,
}) {
    return (
        <div className={`floating-toolbar ${isSidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
            {/* Filter button */}
            <Button
                variant={isFilterOpen ? "default" : "ghost"}
                size="icon"
                onClick={onFilterClick}
                title="Alternar filtros"
                className={`h-10 w-10 relative ${
                    isFilterOpen 
                        ? 'bg-indigo-600 hover:bg-indigo-500 text-white' 
                        : 'hover:bg-slate-700 text-slate-300'
                }`}
            >
                <Filter className="h-5 w-5" />
                {hasActiveFilters && (
                    <Badge 
                        className="absolute -top-1 -right-1 h-5 min-w-5 px-1 text-[10px] bg-indigo-500 hover:bg-indigo-500 border-none"
                    >
                        {activeFilterCount}
                    </Badge>
                )}
            </Button>

            {/* Clear button */}
            <Button
                variant="ghost"
                size="icon"
                onClick={onClearClick}
                title="Limpar todos os eventos"
                className="h-10 w-10 hover:bg-slate-700 text-slate-300 hover:text-red-400"
            >
                <Trash2 className="h-5 w-5" />
            </Button>

            {/* Reset view button */}
            <Button
                variant="ghost"
                size="icon"
                onClick={onResetView}
                title="Resetar visualização (ajustar todos os nós)"
                className="h-10 w-10 hover:bg-slate-700 text-slate-300"
            >
                <Maximize className="h-5 w-5" />
            </Button>
        </div>
    );
}

export default memo(FloatingToolbar);
