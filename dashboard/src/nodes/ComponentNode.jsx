import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import {
    Radio,
    Shield,
    ShieldCheck,
    Search,
    AlertTriangle,
    Key,
    Globe,
    Database,
    Box,
} from 'lucide-react';

const ZONE_COLORS = {
    external: '#6366f1',
    dmz: '#f59e0b',
    internal: '#22c55e',
};

const ICONS = {
    SENSOR: Radio,
    PACKET_FILTER: Shield,
    REVERSE_PROXY: ShieldCheck,
    DISCOVERY: Search,
    IDS: AlertTriangle,
    AUTH: Key,
    EDGE: Globe,
    DATACENTER: Database,
};

function ComponentNode({ data }) {
    const { label, zone, componentType, isActive } = data;
    const borderColor = ZONE_COLORS[zone] || '#64748b';
    const IconComponent = ICONS[componentType] || Box;

    return (
        <div
            style={{
                padding: '12px 16px',
                borderRadius: '8px',
                border: `2px solid ${borderColor}`,
                backgroundColor: isActive ? `${borderColor}22` : '#1e293b',
                minWidth: '120px',
                textAlign: 'center',
                boxShadow: isActive ? `0 0 12px ${borderColor}` : 'none',
                transition: 'all 0.3s ease',
            }}
        >
            <Handle type="target" position={Position.Left} id="left-target" style={{ background: '#64748b' }} />
            <Handle type="source" position={Position.Left} id="left-source" style={{ background: '#64748b' }} />
            <Handle type="target" position={Position.Top} id="top-target" style={{ background: '#64748b' }} />
            <Handle type="source" position={Position.Top} id="top-source" style={{ background: '#64748b' }} />
            <Handle type="target" position={Position.Right} id="right-target" style={{ background: '#64748b' }} />
            <Handle type="source" position={Position.Right} id="right-source" style={{ background: '#64748b' }} />
            <Handle type="target" position={Position.Bottom} id="bottom-target" style={{ background: '#64748b' }} />
            <Handle type="source" position={Position.Bottom} id="bottom-source" style={{ background: '#64748b' }} />
            <div style={{ marginBottom: '4px', display: 'flex', justifyContent: 'center' }}>
                <IconComponent size={24} color="#e2e8f0" />
            </div>
            <div style={{ fontSize: '12px', fontWeight: 'bold', color: '#e2e8f0' }}>
                {label}
            </div>
            <div style={{ fontSize: '10px', color: '#94a3b8', textTransform: 'uppercase' }}>
                {zone}
            </div>
        </div>
    );
}

export default memo(ComponentNode);
