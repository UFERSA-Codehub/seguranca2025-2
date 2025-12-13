import { useState, useCallback, useMemo, useRef, useEffect, forwardRef } from 'react';
import {
    ReactFlow,
    Controls,
    Background,
    useNodesState,
    useEdgesState,
    ReactFlowProvider,
    useReactFlow,
    MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { ChevronDown, ChevronUp, X } from 'lucide-react';

import InfoNode from '@/nodes/InfoNode';
import InfoEdge from '@/edges/InfoEdge';
import { NODE_INFO, EDGE_INFO, ZONES, NODE_POSITIONS, ZONE_BOUNDS } from '@/data/architectureInfo';

function ZoneNode({ data }) {
    const { zone, bounds } = data;
    const zoneInfo = ZONES[zone];
    
    return (
        <div
            style={{
                width: bounds.width,
                height: bounds.height,
                backgroundColor: zoneInfo.fill,
                border: `1px solid ${zoneInfo.borderColor}`,
                borderRadius: '8px',
                position: 'relative',
            }}
        >
            <span
                style={{
                    position: 'absolute',
                    top: '12px',
                    left: '12px',
                    fontSize: '12px',
                    fontWeight: 'bold',
                    color: zoneInfo.color,
                    fontFamily: 'sans-serif',
                }}
            >
                {zoneInfo.label}
            </span>
        </div>
    );
}

const nodeTypes = { info: InfoNode, zone: ZoneNode };
const edgeTypes = { info: InfoEdge };

const bidirectionalMarkers = {
    markerStart: { type: MarkerType.ArrowClosed, width: 12, height: 12, color: '#64748b', orient: 'auto-start-reverse' },
    markerEnd: { type: MarkerType.ArrowClosed, width: 12, height: 12, color: '#64748b' },
};

const unidirectionalMarker = {
    markerEnd: { type: MarkerType.ArrowClosed, width: 12, height: 12, color: '#64748b' },
};

const InfoPanel = forwardRef(function InfoPanel({ selectedNode, selectedEdge, onClose, isExpanded, onToggleExpand }, ref) {
    if (selectedNode) {
        const info = NODE_INFO[selectedNode];
        if (!info) return null;
        
        return (
            <div ref={ref} className={`architecture-info-panel ${isExpanded ? 'expanded' : 'collapsed'}`}>
                <div className="info-panel-header" onClick={onToggleExpand}>
                    <div className="info-panel-title">
                        <span className="info-panel-label">{info.label}</span>
                        <span className="info-panel-zone" style={{ color: ZONES[info.zone]?.color }}>
                            {ZONES[info.zone]?.label}
                        </span>
                    </div>
                    <div className="info-panel-actions">
                        <button onClick={(e) => { e.stopPropagation(); onClose(); }} className="info-panel-close">
                            <X size={16} />
                        </button>
                        {isExpanded ? <ChevronDown size={20} /> : <ChevronUp size={20} />}
                    </div>
                </div>
                
                {isExpanded && (
                    <div className="info-panel-content">
                        <div className="info-section">
                            <h4>Propósito</h4>
                            <p>{info.purpose}</p>
                        </div>
                        
                        <div className="info-section">
                            <h4>Protocolo</h4>
                            <p>{info.protocol}</p>
                        </div>
                        
                        <div className="info-section">
                            <h4>Funcionalidades</h4>
                            <ul>
                                {info.capabilities.map((cap, idx) => (
                                    <li key={idx}>{cap}</li>
                                ))}
                            </ul>
                        </div>
                        
                        <div className="info-section">
                            <h4>Segurança</h4>
                            <p>{info.security}</p>
                        </div>
                        
                        <div className="info-section">
                            <h4>Tipos de Mensagem</h4>
                            <div className="message-types">
                                {info.messageTypes.map((type, idx) => (
                                    <span key={idx} className="message-type-badge">{type}</span>
                                ))}
                            </div>
                        </div>
                    </div>
                )}
            </div>
        );
    }

    if (selectedEdge) {
        const info = EDGE_INFO[selectedEdge];
        if (!info) return null;
        
        const sourceLabel = NODE_INFO[info.source]?.label || info.source;
        const targetLabel = NODE_INFO[info.target]?.label || info.target;
        
        return (
            <div ref={ref} className={`architecture-info-panel ${isExpanded ? 'expanded' : 'collapsed'}`}>
                <div className="info-panel-header" onClick={onToggleExpand}>
                    <div className="info-panel-title">
                        <span className="info-panel-label">{sourceLabel} → {targetLabel}</span>
                        <span className={`info-panel-protocol ${info.protocol.toLowerCase()}`}>
                            {info.protocol}
                        </span>
                    </div>
                    <div className="info-panel-actions">
                        <button onClick={(e) => { e.stopPropagation(); onClose(); }} className="info-panel-close">
                            <X size={16} />
                        </button>
                        {isExpanded ? <ChevronDown size={20} /> : <ChevronUp size={20} />}
                    </div>
                </div>
                
                {isExpanded && (
                    <div className="info-panel-content">
                        <div className="info-section">
                            <h4>Descrição</h4>
                            <p>{info.description}</p>
                        </div>
                        
                        <div className="info-section">
                            <h4>Fluxo de Dados</h4>
                            <p>{info.dataFlow}</p>
                        </div>
                        
                        <div className="info-section">
                            <h4>Tipos de Mensagem</h4>
                            <div className="message-types">
                                {info.messageTypes.map((type, idx) => (
                                    <span key={idx} className="message-type-badge">{type}</span>
                                ))}
                            </div>
                        </div>
                    </div>
                )}
            </div>
        );
    }

    return (
        <div ref={ref} className="architecture-info-panel collapsed hint">
            <div className="info-panel-header">
                <span className="info-panel-hint">Clique em um componente ou conexão para ver detalhes</span>
            </div>
        </div>
    );
});

function ArchitectureContent() {
    const reactFlowInstance = useReactFlow();
    const diagramRef = useRef(null);
    const panelRef = useRef(null);
    const isInitialized = useRef(false);
    const pendingFitView = useRef(false);
    
    const [selectedNode, setSelectedNode] = useState(null);
    const [selectedEdge, setSelectedEdge] = useState(null);
    const [isPanelExpanded, setIsPanelExpanded] = useState(true);

    const handleNodeClick = useCallback((nodeId) => {
        setSelectedNode(nodeId);
        setSelectedEdge(null);
    }, []);

    const handleReactFlowEdgeClick = useCallback((event, edge) => {
        event.stopPropagation();
        setSelectedEdge(edge.id);
        setSelectedNode(null);
    }, []);

    const handleClearSelection = useCallback(() => {
        setSelectedNode(null);
        setSelectedEdge(null);
    }, []);

    const handleTogglePanelExpand = useCallback(() => {
        setIsPanelExpanded(prev => !prev);
    }, []);

    const doFitView = useCallback((duration = 0) => {
        if (!reactFlowInstance) return;
        reactFlowInstance.fitView({ 
            padding: 0.08, 
            duration 
        });
    }, [reactFlowInstance]);

    useEffect(() => {
        const panel = panelRef.current;
        if (!panel) return;

        const handleTransitionEnd = (e) => {
            if (e.target === panel && e.propertyName === 'max-height' && pendingFitView.current) {
                pendingFitView.current = false;
                doFitView(200);
            }
        };

        panel.addEventListener('transitionend', handleTransitionEnd);
        return () => panel.removeEventListener('transitionend', handleTransitionEnd);
    }, [doFitView]);

    useEffect(() => {
        if (!isInitialized.current) return;
        pendingFitView.current = true;
    }, [isPanelExpanded]);

    const hasSelection = selectedNode || selectedEdge;
    const prevHasSelection = useRef(hasSelection);
    
    useEffect(() => {
        if (!isInitialized.current) return;
        if (prevHasSelection.current !== hasSelection) {
            pendingFitView.current = true;
        }
        prevHasSelection.current = hasSelection;
    }, [hasSelection]);

    useEffect(() => {
        if (!diagramRef.current || !reactFlowInstance) return;
        
        let resizeTimeout;
        const observer = new ResizeObserver(() => {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(() => {
                if (isInitialized.current && !pendingFitView.current) {
                    doFitView(0);
                }
            }, 50);
        });
        
        observer.observe(diagramRef.current);
        return () => {
            clearTimeout(resizeTimeout);
            observer.disconnect();
        };
    }, [reactFlowInstance, doFitView]);

    const zoneNodes = useMemo(() => {
        return Object.entries(ZONE_BOUNDS).map(([zoneId, bounds]) => ({
            id: `zone-${zoneId}`,
            type: 'zone',
            position: { x: bounds.x, y: bounds.y },
            data: { zone: zoneId, bounds },
            selectable: false,
            draggable: false,
            zIndex: -1,
        }));
    }, []);

    const componentNodes = useMemo(() => {
        return Object.entries(NODE_INFO).map(([nodeId, info]) => ({
            id: nodeId,
            type: 'info',
            position: NODE_POSITIONS[nodeId] || { x: 0, y: 0 },
            selected: selectedNode === nodeId,
            zIndex: 1,
            data: {
                label: info.label,
                zone: info.zone,
                nodeId: nodeId,
                shape: info.shape,
                onClick: handleNodeClick,
            },
        }));
    }, [selectedNode, handleNodeClick]);

    const initialNodes = useMemo(() => {
        return [...zoneNodes, ...componentNodes];
    }, [zoneNodes, componentNodes]);

    const initialEdges = useMemo(() => {
        const edgeConfigs = [
            { id: 'e-sensors-pf', source: 'SENSORS', target: 'PACKET_FILTER', sourceHandle: 'right-source', targetHandle: 'left-target' },
            { id: 'e-clients-pf', source: 'CLIENTS', target: 'PACKET_FILTER', sourceHandle: 'right-source', targetHandle: 'left-target' },
            { id: 'e-pf-discovery', source: 'PACKET_FILTER', target: 'DISCOVERY', sourceHandle: 'bottom-source', targetHandle: 'left-target' },
            { id: 'e-pf-rp', source: 'PACKET_FILTER', target: 'REVERSE_PROXY', sourceHandle: 'right-source', targetHandle: 'left-target' },
            { id: 'e-pf-ids', source: 'PACKET_FILTER', target: 'IDS', sourceHandle: 'top-source', targetHandle: 'bottom-target' },
            { id: 'e-rp-discovery', source: 'REVERSE_PROXY', target: 'DISCOVERY', sourceHandle: 'bottom-source', targetHandle: 'top-target' },
            { id: 'e-rp-ids', source: 'REVERSE_PROXY', target: 'IDS', sourceHandle: 'top-source', targetHandle: 'bottom-target' },
            { id: 'e-rp-edge', source: 'REVERSE_PROXY', target: 'EDGE', sourceHandle: 'right-source', targetHandle: 'left-target' },
            { id: 'e-rp-dc', source: 'REVERSE_PROXY', target: 'DATACENTER', sourceHandle: 'right-source', targetHandle: 'left-target' },
            { id: 'e-rp-auth', source: 'REVERSE_PROXY', target: 'AUTH', sourceHandle: 'right-source', targetHandle: 'left-target' },
            { id: 'e-ids-edge', source: 'IDS', target: 'EDGE', sourceHandle: 'right-source', targetHandle: 'left-target', unidirectional: true },
            { id: 'e-edge-dc', source: 'EDGE', target: 'DATACENTER', sourceHandle: 'bottom-source', targetHandle: 'top-target', unidirectional: true },
            { id: 'e-dc-auth', source: 'DATACENTER', target: 'AUTH', sourceHandle: 'bottom-source', targetHandle: 'top-target' },
        ];

        return edgeConfigs.map(config => {
            const edgeInfo = EDGE_INFO[config.id];
            const markers = config.unidirectional ? unidirectionalMarker : bidirectionalMarkers;
            
            return {
                id: config.id,
                source: config.source,
                target: config.target,
                sourceHandle: config.sourceHandle,
                targetHandle: config.targetHandle,
                type: 'info',
                selected: selectedEdge === config.id,
                data: {
                    protocol: edgeInfo?.protocol || 'TCP',
                    edgeId: config.id,
                },
                ...markers,
            };
        });
    }, [selectedEdge]);

    const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

    useMemo(() => {
        setNodes(initialNodes);
    }, [initialNodes, setNodes]);

    useMemo(() => {
        setEdges(initialEdges);
    }, [initialEdges, setEdges]);

    const handlePaneClick = useCallback(() => {
        handleClearSelection();
    }, [handleClearSelection]);

    const handleInit = useCallback(() => {
        isInitialized.current = true;
    }, []);

    return (
        <div className="architecture-page">
            <div className="architecture-legend">
                <div className="legend-item">
                    <svg width="40" height="2">
                        <line x1="0" y1="1" x2="40" y2="1" stroke="#94a3b8" strokeWidth="2" />
                    </svg>
                    <span>TCP</span>
                </div>
                <div className="legend-item">
                    <svg width="40" height="2">
                        <line x1="0" y1="1" x2="40" y2="1" stroke="#94a3b8" strokeWidth="2" strokeDasharray="5,5" />
                    </svg>
                    <span>UDP</span>
                </div>
            </div>

            <div className="architecture-diagram" ref={diagramRef}>
                <ReactFlow
                    nodes={nodes}
                    edges={edges}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onPaneClick={handlePaneClick}
                    onEdgeClick={handleReactFlowEdgeClick}
                    onInit={handleInit}
                    nodeTypes={nodeTypes}
                    edgeTypes={edgeTypes}
                    fitView
                    fitViewOptions={{ padding: 0.08 }}
                    nodesDraggable={false}
                    nodesConnectable={false}
                    elementsSelectable={true}
                    panOnDrag={true}
                    zoomOnScroll={true}
                    proOptions={{ hideAttribution: true }}
                >
                    <Background color="#334155" gap={20} />
                    <Controls showInteractive={false} />
                </ReactFlow>
            </div>

            <InfoPanel
                ref={panelRef}
                selectedNode={selectedNode}
                selectedEdge={selectedEdge}
                onClose={handleClearSelection}
                isExpanded={isPanelExpanded}
                onToggleExpand={handleTogglePanelExpand}
            />
        </div>
    );
}

export default function ArchitecturePage() {
    return (
        <ReactFlowProvider>
            <ArchitectureContent />
        </ReactFlowProvider>
    );
}
