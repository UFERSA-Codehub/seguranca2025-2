import { useState, useCallback, useMemo, useEffect } from 'react';
import {
    ReactFlow,
    Controls,
    Background,
    useNodesState,
    useEdgesState,
    MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { useTraceEvents } from './hooks/useTraceEvents';
import { usePlayback } from './hooks/usePlayback';
import ComponentNode from './nodes/ComponentNode';
import AnimatedEdge from './edges/AnimatedEdge';
import PlaybackControls from './components/PlaybackControls';
import './App.css';

const nodeTypes = { component: ComponentNode };
const edgeTypes = { animated: AnimatedEdge };

const initialNodes = [
    { id: 'SENSOR', type: 'component', position: { x: 80, y: 280 }, data: { label: 'Sensor', zone: 'external', componentType: 'SENSOR' } },
    { id: 'DISCOVERY', type: 'component', position: { x: 420, y: 50 }, data: { label: 'Discovery', zone: 'dmz', componentType: 'DISCOVERY' } },
    { id: 'IDS', type: 'component', position: { x: 520, y: 170 }, data: { label: 'IDS', zone: 'dmz', componentType: 'IDS' } },
    { id: 'PACKET_FILTER', type: 'component', position: { x: 300, y: 350 }, data: { label: 'PacketFilter', zone: 'dmz', componentType: 'PACKET_FILTER' } },
    { id: 'REVERSE_PROXY', type: 'component', position: { x: 520, y: 350 }, data: { label: 'ReverseProxy', zone: 'dmz', componentType: 'REVERSE_PROXY' } },
    { id: 'EDGE', type: 'component', position: { x: 720, y: 100 }, data: { label: 'Edge', zone: 'internal', componentType: 'EDGE' } },
    { id: 'DATACENTER', type: 'component', position: { x: 720, y: 260 }, data: { label: 'Datacenter', zone: 'internal', componentType: 'DATACENTER' } },
    { id: 'AUTH', type: 'component', position: { x: 720, y: 420 }, data: { label: 'AuthServer', zone: 'internal', componentType: 'AUTH' } },
];

const defaultEdgeOptions = {
    type: 'animated',
    markerEnd: { type: MarkerType.ArrowClosed, width: 15, height: 15, color: '#64748b' },
};

const initialEdges = [
    { id: 'e-sensor-discovery', source: 'SENSOR', target: 'DISCOVERY', sourceHandle: 'top-source', targetHandle: 'left-target', data: { protocol: 'UDP' } },
    { id: 'e-sensor-pf', source: 'SENSOR', target: 'PACKET_FILTER', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' } },
    { id: 'e-pf-rp', source: 'PACKET_FILTER', target: 'REVERSE_PROXY', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' } },
    { id: 'e-rp-ids', source: 'REVERSE_PROXY', target: 'IDS', sourceHandle: 'top-source', targetHandle: 'bottom-target', data: { protocol: 'TCP' } },
    { id: 'e-rp-edge', source: 'REVERSE_PROXY', target: 'EDGE', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' } },
    { id: 'e-rp-dc', source: 'REVERSE_PROXY', target: 'DATACENTER', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' } },
    { id: 'e-rp-auth', source: 'REVERSE_PROXY', target: 'AUTH', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' } },
    { id: 'e-ids-edge', source: 'IDS', target: 'EDGE', sourceHandle: 'right-source', targetHandle: 'left-target', data: { protocol: 'TCP' } },
    { id: 'e-edge-dc', source: 'EDGE', target: 'DATACENTER', sourceHandle: 'bottom-source', targetHandle: 'top-target', data: { protocol: 'TCP' } },
    { id: 'e-auth-discovery', source: 'AUTH', target: 'DISCOVERY', sourceHandle: 'top-source', targetHandle: 'right-target', data: { protocol: 'UDP' } },
    { id: 'e-edge-discovery', source: 'EDGE', target: 'DISCOVERY', sourceHandle: 'top-source', targetHandle: 'right-target', data: { protocol: 'UDP' } },
    { id: 'e-dc-discovery', source: 'DATACENTER', target: 'DISCOVERY', sourceHandle: 'top-source', targetHandle: 'right-target', data: { protocol: 'UDP' } },
];

function normalizeComponentId(id) {
    if (!id) return null;
    if (id.startsWith('SENSOR')) return 'SENSOR';
    if (id.startsWith('CLIENT')) return 'SENSOR';
    if (id.startsWith('MALICIOUS')) return 'SENSOR';
    return id;
}

function getAnimationEdge(event, edges) {
    if (!event) return null;

    const component = event.componentType;
    const peer = normalizeComponentId(event.peerId);
    
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

    return edge ? edge.id : null;
}

function App() {
    const { events, connected, bufferFull, clearEvents } = useTraceEvents();
    const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
    const [selectedEvent, setSelectedEvent] = useState(null);

    const playback = usePlayback(events);
    const { currentEvent, currentIndex, isAnimating } = playback;

    const animatingEdgeId = useMemo(() => {
        if (!isAnimating || !currentEvent) return null;
        return getAnimationEdge(currentEvent, initialEdges);
    }, [isAnimating, currentEvent]);

    const activeComponents = useMemo(() => {
        if (!currentEvent) return new Set();
        const active = new Set();
        if (currentEvent.componentType) active.add(currentEvent.componentType);
        const peer = normalizeComponentId(currentEvent.peerId);
        if (peer) active.add(peer);
        return active;
    }, [currentEvent]);

    useEffect(() => {
        setNodes((nds) =>
            nds.map((node) => ({
                ...node,
                data: {
                    ...node.data,
                    isActive: activeComponents.has(node.id) || activeComponents.has(node.data.componentType),
                },
            }))
        );
    }, [activeComponents, setNodes]);

    useEffect(() => {
        setEdges((eds) =>
            eds.map((edge) => ({
                ...edge,
                data: {
                    ...edge.data,
                    isAnimating: edge.id === animatingEdgeId,
                },
            }))
        );
    }, [animatingEdgeId, setEdges]);

    const handleEventClick = useCallback((event, eventIndex) => {
        setSelectedEvent(event);
        playback.seekTo(eventIndex);
    }, [playback]);

    const formatPayload = useCallback((payload) => {
        if (!payload) return 'N/A';
        try {
            const parsed = JSON.parse(payload);
            return JSON.stringify(parsed, null, 2);
        } catch {
            return payload.length > 200 ? payload.substring(0, 200) + '...' : payload;
        }
    }, []);

    const isEventActive = useCallback((eventIndex) => {
        return eventIndex === currentIndex;
    }, [currentIndex]);

    return (
        <div className="app">
            <div className="topology">
                <ReactFlow
                    nodes={nodes}
                    edges={edges}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    nodeTypes={nodeTypes}
                    edgeTypes={edgeTypes}
                    defaultEdgeOptions={defaultEdgeOptions}
                    fitView
                    proOptions={{ hideAttribution: true }}
                >
                    <Background color="#334155" gap={20} />
                    <Controls />
                </ReactFlow>
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
                />
            </div>

            <div className="sidebar">
                <div className="header">
                    <h2>Trace Events</h2>
                    <div className="status">
                        <span className={`dot ${connected ? 'connected' : 'disconnected'}`} />
                        {connected ? 'Connected' : 'Disconnected'}
                    </div>
                    {bufferFull && <span className="buffer-badge">Buffer Full</span>}
                    <button onClick={clearEvents} className="clear-btn">Clear</button>
                </div>

                <div className="events-list">
                    {events.map((event, idx) => (
                        <div
                            key={event.traceId || idx}
                            className={`event-item ${selectedEvent?.traceId === event.traceId ? 'selected' : ''} ${isEventActive(idx) ? 'active' : ''}`}
                            onClick={() => handleEventClick(event, idx)}
                        >
                            <div className="event-header">
                                <span className="event-flow">
                                    {event.componentType || event.componentId}
                                    {event.direction === 'RECEIVE' ? ' ← ' : ' → '}
                                    {event.peerId || '?'}
                                </span>
                                <span className="message-type">{event.messageType}</span>
                            </div>
                            <div className="event-meta">
                                <span>{event.protocol}</span>
                            </div>
                        </div>
                    ))}
                    {events.length === 0 && (
                        <div className="no-events">No events yet. Start the system with tracing enabled.</div>
                    )}
                </div>

                {selectedEvent && (
                    <div className="payload-panel">
                        <h3>Payload Details</h3>
                        <div className="payload-section">
                            <h4>Encrypted</h4>
                            <pre>{formatPayload(selectedEvent.encryptedPayload)}</pre>
                        </div>
                        <div className="payload-section">
                            <h4>Decrypted</h4>
                            <pre>{formatPayload(selectedEvent.decryptedPayload)}</pre>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

export default App;
