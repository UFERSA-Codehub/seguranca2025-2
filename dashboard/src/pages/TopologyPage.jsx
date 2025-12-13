import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import {
    ReactFlow,
    Controls,
    Background,
    useNodesState,
    useEdgesState,
    useReactFlow,
    ReactFlowProvider,
    MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useTrace, normalizeAddress } from '@/contexts/TraceContext';
import { usePlayback } from '@/hooks/usePlayback';
import { AnimationProvider, useAnimationContext } from '@/hooks/useAnimationContext';
import ComponentNode from '@/nodes/ComponentNode';
import AnimatedEdge from '@/edges/AnimatedEdge';
import PlaybackControls from '@/components/PlaybackControls';
import Sidebar from '@/components/Sidebar';
import FloatingToolbar from '@/components/FloatingToolbar';
import FilterOverlay from '@/components/FilterOverlay';
const DEBUG = false;
const log = (...args) => DEBUG && console.log('[Topology]', ...args);
const nodeTypes = { component: ComponentNode };
const edgeTypes = { animated: AnimatedEdge };
const staticNodes = [
    { id: 'DISCOVERY', type: 'component', position: { x: 400, y: 600 }, data: { label: 'Discovery', zone: 'shared', componentType: 'DISCOVERY' } },
    { id: 'IDS', type: 'component', position: { x: 420, y: 130 }, data: { label: 'IDS', zone: 'dmz', componentType: 'IDS' } },
    { id: 'PACKET_FILTER', type: 'component', position: { x: 300, y: 300 }, data: { label: 'PacketFilter', zone: 'dmz', componentType: 'PACKET_FILTER' } },
    { id: 'REVERSE_PROXY', type: 'component', position: { x: 500, y: 300 }, data: { label: 'ReverseProxy', zone: 'dmz', componentType: 'REVERSE_PROXY' } },
    { id: 'EDGE', type: 'component', position: { x: 700, y: 130 }, data: { label: 'Edge', zone: 'internal', componentType: 'EDGE' } },
    { id: 'DATACENTER', type: 'component', position: { x: 700, y: 300 }, data: { label: 'Datacenter', zone: 'internal', componentType: 'DATACENTER' } },
    { id: 'AUTH', type: 'component', position: { x: 700, y: 470 }, data: { label: 'AuthServer', zone: 'internal', componentType: 'AUTH' } },
];
const defaultEdgeOptions = {
    type: 'animated',
    markerEnd: { type: MarkerType.ArrowClosed, width: 15, height: 15, color: '#64748b' },
};
const bidirectionalMarkers = {
    markerStart: { type: MarkerType.ArrowClosed, width: 15, height: 15, color: '#64748b', orient: 'auto-start-reverse' },
    markerEnd: { type: MarkerType.ArrowClosed, width: 15, height: 15, color: '#64748b' },
};
const staticEdges = [
    { id: 'e-pf-rp', source: 'PACKET_FILTER', target: 'REVERSE_PROXY', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' }, ...bidirectionalMarkers },
    { id: 'e-pf-ids', source: 'PACKET_FILTER', target: 'IDS', sourceHandle: 'top-source', targetHandle: 'bottom-target', data: { protocol: 'TCP' } },
    { id: 'e-pf-discovery', source: 'PACKET_FILTER', target: 'DISCOVERY', sourceHandle: 'bottom-source', targetHandle: 'left-target', data: { protocol: 'UDP' }, ...bidirectionalMarkers },
    { id: 'e-rp-ids', source: 'REVERSE_PROXY', target: 'IDS', sourceHandle: 'top-source', targetHandle: 'bottom-target', data: { protocol: 'TCP' } },
    { id: 'e-rp-discovery', source: 'REVERSE_PROXY', target: 'DISCOVERY', sourceHandle: 'bottom-source', targetHandle: 'top-target', data: { protocol: 'UDP' }, ...bidirectionalMarkers },
    { id: 'e-rp-edge', source: 'REVERSE_PROXY', target: 'EDGE', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' }, ...bidirectionalMarkers },
    { id: 'e-rp-dc', source: 'REVERSE_PROXY', target: 'DATACENTER', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' }, ...bidirectionalMarkers },
    { id: 'e-rp-auth', source: 'REVERSE_PROXY', target: 'AUTH', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' }, ...bidirectionalMarkers },
    { id: 'e-ids-edge', source: 'IDS', target: 'EDGE', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' } },
    { id: 'e-edge-dc', source: 'EDGE', target: 'DATACENTER', sourceHandle: 'bottom-source', targetHandle: 'top-target', data: { protocol: 'TCP' }, ...bidirectionalMarkers },
    { id: 'e-dc-auth', source: 'DATACENTER', target: 'AUTH', sourceHandle: 'bottom-source', targetHandle: 'top-target', data: { protocol: 'TCP' }, ...bidirectionalMarkers },
];
function isExternalClient(id) {
    if (!id) return false;
    return /^(SENSOR|CLIENT|CLI|MALICIOUS)_/.test(id) || id === 'BROWSER';
}
function isIpAddress(id) {
    if (!id) return false;
    return /^\/?(\d{1,3}\.){3}\d{1,3}:\d+$/.test(id);
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
function getAnimationEdge(event, edges, addressToClientMap) {
    if (!event) return null;
    const component = normalizeForTopology(event.componentType, addressToClientMap);
    const peer = normalizeForTopology(event.peerId, addressToClientMap);
    if (!component || !peer) return null;
    let source, target;
    if (event.direction === 'RECEIVE') {
        source = peer;
        target = component;
    } else {
        source = component;
        target = peer;
    }
    const edge = edges.find(
        (e) => (e.source === source && e.target === target) || (e.source === target && e.target === source)
    );
    if (!edge) return null;
    const isReversed = edge.source !== source;
    return { edgeId: edge.id, isReversed };
}
function getEventEntities(event, addressToClientMap) {
    const entities = new Set();
    const component = normalizeForTopology(event.componentType, addressToClientMap);
    const peer = normalizeForTopology(event.peerId, addressToClientMap);
    if (component) entities.add(component);
    if (peer) entities.add(peer);
    return entities;
}
function getVisibleNodes(selectedEntities, excludedEntities, allEvents, allNodeIds, addressToClientMap) {
    let visible = new Set(allNodeIds);
    if (excludedEntities.size > 0) {
        excludedEntities.forEach(e => visible.delete(e));
    }
    if (selectedEntities.size > 0) {
        const included = new Set();
        selectedEntities.forEach(e => included.add(e));
        for (const event of allEvents) {
            const entities = getEventEntities(event, addressToClientMap);
            const hasSelectedEntity = [...entities].some(e => selectedEntities.has(e));
            if (hasSelectedEntity) {
                entities.forEach(e => included.add(e));
            }
        }
        visible = new Set([...visible].filter(id => included.has(id)));
    }
    return visible;
}
function TopologyContent() {
    const reactFlowInstance = useReactFlow();
    useEffect(() => {
        log('TopologyContent MOUNTED');
        return () => log('TopologyContent UNMOUNTING');
    }, []);
    const {
        events,
        allEvents,
        filteredAllEvents,
        connected,
        hasReceivedInitialData,
        bufferFull,
        clearEvents,
        currentPage,
        totalPages,
        pageSize,
        goToPage,
        nextPage,
        prevPage,
        firstPage,
        lastPage,
        pauseUpdates,
        resumeUpdates,
        isPaused,
        bufferedEventCount,
        savedViewport,
        setSavedViewport,
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
    } = useTrace();
    const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
    const [isFilterOpen, setIsFilterOpen] = useState(false);
    const playback = usePlayback(filteredAllEvents, {
        onPlayStart: pauseUpdates,
        onPlayStop: resumeUpdates,
    });
    const { currentEvent, currentIndex, isAnimating } = playback;
    useEffect(() => {
        if (!isAnimating || currentIndex < 0) return;
        const targetPage = Math.floor(currentIndex / pageSize);
        if (targetPage !== currentPage) {
            goToPage(targetPage);
        }
    }, [isAnimating, currentIndex, pageSize, currentPage, goToPage]);
    const externalClients = useMemo(() => {
        const clients = new Set();
        for (const event of allEvents) {
            if (isExternalClient(event.peerId)) {
                clients.add(event.peerId);
            }
            if (isExternalClient(event.componentType)) {
                clients.add(event.componentType);
            }
        }
        const sorted = Array.from(clients).sort();
        log('Computed externalClients:', sorted.length, 'from allEvents:', allEvents.length);
        return sorted;
    }, [allEvents]);
    const clientToAddressMap = useMemo(() => {
        const map = new Map();
        for (const event of allEvents) {
            if (isExternalClient(event.peerId) && event.remoteAddress && !isExternalClient(event.componentType)) {
                const addr = normalizeAddress(event.remoteAddress);
                if (addr && !map.has(event.peerId)) {
                    map.set(event.peerId, addr);
                    log(`[ClientToAddr] ${event.peerId} → ${addr} (from server event)`);
                }
            }
            if (isExternalClient(event.componentType) && event.localAddress) {
                const addr = normalizeAddress(event.localAddress);
                if (addr && !map.has(event.componentType)) {
                    map.set(event.componentType, addr);
                    log(`[ClientToAddr] ${event.componentType} → ${addr} (from client localAddress)`);
                }
            }
        }
        log('[ClientToAddr] Final map:', Object.fromEntries(map));
        return map;
    }, [allEvents]);
    const allEntityOptions = useMemo(() => {
        const entities = new Set();
        staticNodes.forEach(node => entities.add(node.id));
        externalClients.forEach(client => entities.add(client));
        return Array.from(entities).sort();
    }, [externalClients]);
    const allMessageTypeOptions = useMemo(() => {
        const types = new Set();
        for (const event of allEvents) {
            if (event.messageType) {
                types.add(event.messageType);
            }
        }
        return Array.from(types).sort();
    }, [allEvents]);
    const handleToggleSidebar = useCallback(() => {
        setIsSidebarCollapsed(prev => !prev);
    }, []);
    const handleToggleFilter = useCallback(() => {
        setIsFilterOpen(prev => !prev);
    }, []);
    const handleCloseFilter = useCallback(() => {
        setIsFilterOpen(false);
    }, []);
    const allNodeIds = useMemo(() => {
        return [...staticNodes.map(n => n.id), ...externalClients];
    }, [externalClients]);
    const visibleNodeIds = useMemo(() => {
        return getVisibleNodes(selectedEntities, excludedEntities, allEvents, allNodeIds, addressToClientMap);
    }, [selectedEntities, excludedEntities, allEvents, allNodeIds, addressToClientMap]);
    const dynamicNodes = useMemo(() => {
        return externalClients.map((clientId, index) => {
            const label = clientId.replace(/_/g, ' ').replace(/\b\w/g, (c) => c);
            const isMalicious = clientId.startsWith('MALICIOUS');
            const address = clientToAddressMap.get(clientId);
            return {
                id: clientId,
                type: 'component',
                position: { x: 80, y: 100 + index * 120 },
                data: {
                    label: label,
                    zone: isMalicious ? 'malicious' : 'external',
                    componentType: clientId,
                    address: address,
                },
            };
        });
    }, [externalClients, clientToAddressMap]);
    const dynamicEdges = useMemo(() => {
        const edges = [];
        externalClients.forEach((clientId) => {
            edges.push({
                id: `e-${clientId}-pf`,
                source: clientId,
                target: 'PACKET_FILTER',
                sourceHandle: 'right-source',
                targetHandle: 'left-target',
                data: { protocol: 'TCP + UDP' },
                ...bidirectionalMarkers,
            });
        });
        return edges;
    }, [externalClients]);
    const allNodesBase = useMemo(() => [...staticNodes, ...dynamicNodes], [dynamicNodes]);
    const allEdgesBase = useMemo(() => [...staticEdges, ...dynamicEdges], [dynamicEdges]);
    const [nodes, setNodes, onNodesChange] = useNodesState(allNodesBase);
    const [edges, setEdges, onEdgesChange] = useEdgesState(allEdgesBase);
    const reactFlowInitialized = useRef(false);
    const [isReady, setIsReady] = useState(false);
    const [dataReady, setDataReady] = useState(false);
    const hasInitialFitView = useRef(false);
    const initialSavedViewport = useRef(savedViewport);
    log('Component init - savedViewport:', savedViewport, 'initialSavedViewport:', initialSavedViewport.current);
    useEffect(() => {
        if (dataReady) return;
        if (hasReceivedInitialData || externalClients.length > 0) {
            const timer = setTimeout(() => {
                setDataReady(true);
            }, 100);
            return () => clearTimeout(timer);
        }
        const timeout = setTimeout(() => {
            setDataReady(true);
        }, 3000);
        return () => clearTimeout(timeout);
    }, [hasReceivedInitialData, externalClients.length, dataReady]);
    useEffect(() => {
        if (!reactFlowInitialized.current) return;
        const hasEntityFilters = selectedEntities.size > 0 || excludedEntities.size > 0;
        const newNodes = [...staticNodes, ...dynamicNodes].map(node => ({
            ...node,
            hidden: hasEntityFilters ? !visibleNodeIds.has(node.id) : false,
        }));
        setNodes(newNodes);
    }, [dynamicNodes, setNodes, selectedEntities, excludedEntities, visibleNodeIds]);
    useEffect(() => {
        if (!reactFlowInitialized.current) return;
        const hasEntityFilters = selectedEntities.size > 0 || excludedEntities.size > 0;
        const newEdges = [...staticEdges, ...dynamicEdges].map(edge => ({
            ...edge,
            hidden: hasEntityFilters ? (!visibleNodeIds.has(edge.source) || !visibleNodeIds.has(edge.target)) : false,
        }));
        setEdges(newEdges);
    }, [dynamicEdges, setEdges, selectedEntities, excludedEntities, visibleNodeIds]);
    const animationInfo = useMemo(() => {
        if (!isAnimating || !currentEvent) return null;
        const edgeInfo = getAnimationEdge(currentEvent, allEdgesBase, addressToClientMap);
        if (!edgeInfo) return null;
        return {
            ...edgeInfo,
            animationKey: playback.animationKey,
        };
    }, [isAnimating, currentEvent, allEdgesBase, playback.animationKey, addressToClientMap]);
    const activeComponents = useMemo(() => {
        if (!currentEvent) return new Set();
        const active = new Set();
        const component = normalizeForTopology(currentEvent.componentType, addressToClientMap);
        const peer = normalizeForTopology(currentEvent.peerId, addressToClientMap);
        if (component) active.add(component);
        if (peer) active.add(peer);
        return active;
    }, [currentEvent, addressToClientMap]);
    const eventAddresses = useMemo(() => {
        if (!currentEvent) return new Map();
        const addresses = new Map();
        const remoteAddr = normalizeAddress(currentEvent.remoteAddress);
        const localAddr = normalizeAddress(currentEvent.localAddress);
        const component = normalizeForTopology(currentEvent.componentType, addressToClientMap);
        const peer = normalizeForTopology(currentEvent.peerId, addressToClientMap);
        if (peer && remoteAddr) {
            if (isExternalClient(peer)) {
                addresses.set(peer, remoteAddr);
            }
            if (isIpAddress(currentEvent.peerId)) {
                const normalizedPeerId = normalizeAddress(currentEvent.peerId);
                const resolvedPeer = addressToClientMap.get(normalizedPeerId);
                if (resolvedPeer) {
                    addresses.set(resolvedPeer, remoteAddr);
                }
            }
        }
        if (isExternalClient(currentEvent.componentType) && localAddr) {
            addresses.set(component, localAddr);
        }
        if (isIpAddress(currentEvent.peerId) && remoteAddr) {
            const normalizedPeerId = normalizeAddress(currentEvent.peerId);
            if (!addressToClientMap.has(normalizedPeerId)) {
                addresses.set(normalizedPeerId, remoteAddr);
            }
        }
        return addresses;
    }, [currentEvent, addressToClientMap]);
    useEffect(() => {
        if (!reactFlowInitialized.current) return;
        setNodes((nds) =>
            nds.map((node) => {
                const shouldBeActive = activeComponents.has(node.id) || activeComponents.has(node.data.componentType);
                const eventAddress = eventAddresses.get(node.id) || eventAddresses.get(node.data.componentType);
                const displayAddress = eventAddress || clientToAddressMap.get(node.id) || clientToAddressMap.get(node.data.componentType);
                if (node.data.isActive === shouldBeActive && node.data.address === displayAddress) {
                    return node;
                }
                return {
                    ...node,
                    data: {
                        ...node.data,
                        isActive: shouldBeActive,
                        address: displayAddress,
                    },
                };
            })
        );
    }, [activeComponents, eventAddresses, clientToAddressMap, setNodes]);
    const { startEdgeAnimation, stopEdgeAnimation } = useAnimationContext();
    useEffect(() => {
        if (animationInfo) {
            log(`Starting edge animation: edgeId=${animationInfo.edgeId}, key=${animationInfo.animationKey}`);
            startEdgeAnimation(animationInfo.edgeId, animationInfo.isReversed, animationInfo.animationKey);
        } else {
            log(`Stopping edge animation`);
            stopEdgeAnimation();
        }
    }, [animationInfo, startEdgeAnimation, stopEdgeAnimation]);
    const handleEventClick = useCallback((event, localIndex) => {
        const globalIndex = currentPage * pageSize + localIndex;
        playback.seekTo(globalIndex);
    }, [playback, currentPage, pageSize]);
    const handleResetView = useCallback(() => {
        if (!reactFlowInstance) return;
        reactFlowInstance.fitView({ padding: 0.15, maxZoom: 0.85, duration: 300 });
    }, [reactFlowInstance]);
    const prevExternalClientsCount = useRef(externalClients.length);
    useEffect(() => {
        log('Initial viewport effect - reactFlowInitialized:', reactFlowInitialized.current, 
            'reactFlowInstance:', !!reactFlowInstance, 
            'hasInitialFitView:', hasInitialFitView.current,
            'externalClients:', externalClients.length,
            'initialSavedViewport:', initialSavedViewport.current);
        if (!reactFlowInitialized.current || !reactFlowInstance) return;
        if (hasInitialFitView.current) return;
        prevExternalClientsCount.current = externalClients.length;
        const hasClients = externalClients.length > 0;
        if (hasClients) {
            hasInitialFitView.current = true;
            setTimeout(() => {
                log('No saved viewport, doing fitView');
                reactFlowInstance.fitView({ padding: 0.15, maxZoom: 0.85, duration: 0 });
            }, 100);
        } else {
            log('No clients yet, setting 2s timeout');
            const timeout = setTimeout(() => {
                if (!hasInitialFitView.current) {
                    hasInitialFitView.current = true;
                    log('Timeout: No saved viewport, doing fitView');
                    reactFlowInstance.fitView({ padding: 0.15, maxZoom: 0.85, duration: 0 });
                }
            }, 2000);
            return () => clearTimeout(timeout);
        }
    }, [reactFlowInstance, externalClients.length, isReady]);
    useEffect(() => {
        if (!reactFlowInitialized.current || !reactFlowInstance) return;
        if (!hasInitialFitView.current) return;
        if (initialSavedViewport.current) return;
        if (externalClients.length > prevExternalClientsCount.current) {
            setTimeout(() => {
                reactFlowInstance.fitView({ padding: 0.15, maxZoom: 0.85, duration: 300 });
            }, 50);
        }
        prevExternalClientsCount.current = externalClients.length;
    }, [reactFlowInstance, externalClients.length, isReady]);
    const reactFlowInstanceRef = useRef(null);
    useEffect(() => {
        reactFlowInstanceRef.current = reactFlowInstance;
    }, [reactFlowInstance]);
    useEffect(() => {
        return () => {
            if (reactFlowInstanceRef.current && hasInitialFitView.current) {
                const viewport = reactFlowInstanceRef.current.getViewport();
                log('Unmount - saving viewport:', viewport);
                setSavedViewport(viewport);
            } else {
                log('Unmount - NOT saving (initial fitView not done)');
            }
        };
    }, [setSavedViewport]);
    const topologyContainerRef = useRef(null);
    const userHasInteracted = useRef(initialSavedViewport.current !== null);
    const handleMoveEnd = useCallback((event, viewport) => {
        if (!hasInitialFitView.current) {
            log('handleMoveEnd - ignoring (initial fitView not done yet):', viewport);
            return;
        }
        log('handleMoveEnd - saving viewport:', viewport);
        userHasInteracted.current = true;
        setSavedViewport(viewport);
    }, [setSavedViewport]);
    useEffect(() => {
        if (!reactFlowInstance || !topologyContainerRef.current) return;
        const observer = new ResizeObserver(() => {
            if (!userHasInteracted.current) {
                reactFlowInstance.fitView({ padding: 0.15, maxZoom: 0.85, duration: 0 });
            }
        });
        observer.observe(topologyContainerRef.current);
        return () => observer.disconnect();
    }, [reactFlowInstance]);
    const showDiagram = isReady && dataReady;
    return (
        <div className="topology-page">
            {!showDiagram && (
                <div className="topology-loading">
                    <div className="loading-spinner" />
                </div>
            )}
            <div className={`topology ${showDiagram ? 'visible' : ''}`} ref={topologyContainerRef}>
                {dataReady && (
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onMoveEnd={handleMoveEnd}
                        nodeTypes={nodeTypes}
                        edgeTypes={edgeTypes}
                        defaultEdgeOptions={defaultEdgeOptions}
                        defaultViewport={initialSavedViewport.current || undefined}
                        onInit={(instance) => {
                            reactFlowInitialized.current = true;
                            setIsReady(true);
                            if (initialSavedViewport.current) {
                                hasInitialFitView.current = true;
                                log('onInit - restored viewport via defaultViewport:', initialSavedViewport.current);
                            }
                        }}
                        proOptions={{ hideAttribution: true }}
                    >
                        <Background color="#334155" gap={20} />
                        <Controls />
                    </ReactFlow>
                )}
                {showDiagram && (
                    <PlaybackControls
                        isPlaying={playback.isPlaying}
                        isAnimating={playback.isAnimating}
                        togglePlay={playback.togglePlay}
                        currentIndex={playback.currentIndex}
                        maxIndex={playback.maxIndex}
                        stepForward={playback.stepForward}
                        stepBackward={playback.stepBackward}
                        jumpToStart={playback.jumpToStart}
                        jumpToEnd={playback.jumpToEnd}
                        seekTo={playback.seekTo}
                        isPaused={isPaused}
                        bufferedCount={bufferedEventCount}
                    />
                )}
            </div>
            {showDiagram && (
                <>
                    <Sidebar
                        isCollapsed={isSidebarCollapsed}
                        onToggle={handleToggleSidebar}
                        events={events}
                        allEvents={allEvents}
                        filteredAllEvents={filteredAllEvents}
                        connected={connected}
                        bufferFull={bufferFull}
                        currentPage={currentPage}
                        totalPages={totalPages}
                        pageSize={pageSize}
                        firstPage={firstPage}
                        prevPage={prevPage}
                        nextPage={nextPage}
                        lastPage={lastPage}
                        currentEvent={currentEvent}
                        onEventClick={handleEventClick}
                        currentIndex={currentIndex}
                        hasActiveFilters={hasActiveFilters}
                        addressToClientMap={addressToClientMap}
                    />
                    <FloatingToolbar
                        onFilterClick={handleToggleFilter}
                        onClearClick={clearEvents}
                        onResetView={handleResetView}
                        isSidebarCollapsed={isSidebarCollapsed}
                        hasActiveFilters={hasActiveFilters}
                        activeFilterCount={activeFilterCount}
                        isFilterOpen={isFilterOpen}
                    />
                    <FilterOverlay
                        isOpen={isFilterOpen}
                        onClose={handleCloseFilter}
                        entities={allEntityOptions}
                        messageTypes={allMessageTypeOptions}
                        selectedEntities={selectedEntities}
                        selectedMessageTypes={selectedMessageTypes}
                        excludedEntities={excludedEntities}
                        excludedMessageTypes={excludedMessageTypes}
                        onToggleEntity={toggleEntity}
                        onToggleMessageType={toggleMessageType}
                        onExcludeEntity={excludeEntity}
                        onExcludeMessageType={excludeMessageType}
                        onClearAll={clearAllFilters}
                        isSidebarCollapsed={isSidebarCollapsed}
                    />
                </>
            )}
        </div>
    );
}
export default function TopologyPage() {
    return (
        <ReactFlowProvider>
            <AnimationProvider>
                <TopologyContent />
            </AnimationProvider>
        </ReactFlowProvider>
    );
}
