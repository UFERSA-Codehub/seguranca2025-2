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
    Users,
} from 'lucide-react';
import { ZONES } from '@/data/architectureInfo';
const ICONS = {
    SENSORS: Radio,
    CLIENTS: Users,
    PACKET_FILTER: Shield,
    REVERSE_PROXY: ShieldCheck,
    DISCOVERY: Search,
    IDS: AlertTriangle,
    AUTH: Key,
    EDGE: Globe,
    DATACENTER: Database,
};
function InfoNode({ data, selected }) {
    const { label, zone, nodeId, shape, onClick } = data;
    const zoneInfo = ZONES[zone] || ZONES.dmz;
    const IconComponent = ICONS[nodeId] || Radio;
    const isCircle = shape === 'circle';
    const handleClick = (e) => {
        e.stopPropagation();
        if (onClick) onClick(nodeId);
    };
    const baseStyle = {
        backgroundColor: selected ? zoneInfo.fill : '#1e293b',
        border: `2px ${isCircle ? 'dashed' : 'solid'} ${selected ? zoneInfo.color : zoneInfo.borderColor}`,
        cursor: 'pointer',
        transition: 'all 0.2s ease',
        boxShadow: selected ? `0 0 16px ${zoneInfo.color}60` : 'none',
    };
    if (isCircle) {
        return (
            <div
                className="info-node info-node-circle"
                style={{
                    ...baseStyle,
                    width: '100px',
                    height: '100px',
                    borderRadius: '50%',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}
                onClick={handleClick}
            >
                {/* Handles for edges */}
                <Handle type="source" position={Position.Left} id="left-source" style={{ background: zoneInfo.color }} />
                <Handle type="target" position={Position.Left} id="left-target" style={{ background: zoneInfo.color }} />
                <Handle type="source" position={Position.Right} id="right-source" style={{ background: zoneInfo.color }} />
                <Handle type="target" position={Position.Right} id="right-target" style={{ background: zoneInfo.color }} />
                <Handle type="source" position={Position.Top} id="top-source" style={{ background: zoneInfo.color }} />
                <Handle type="target" position={Position.Top} id="top-target" style={{ background: zoneInfo.color }} />
                <Handle type="source" position={Position.Bottom} id="bottom-source" style={{ background: zoneInfo.color }} />
                <Handle type="target" position={Position.Bottom} id="bottom-target" style={{ background: zoneInfo.color }} />
                <IconComponent size={28} color="#e2e8f0" />
                <div style={{ fontSize: '12px', fontWeight: 'bold', color: '#e2e8f0', marginTop: '6px' }}>
                    {label}
                </div>
            </div>
        );
    }
    return (
        <div
            className="info-node info-node-rectangle"
            style={{
                ...baseStyle,
                padding: '12px 16px',
                borderRadius: '8px',
                minWidth: '100px',
                textAlign: 'center',
            }}
            onClick={handleClick}
        >
            {/* All handles for flexibility */}
            <Handle type="target" position={Position.Left} id="left-target" style={{ background: zoneInfo.color }} />
            <Handle type="source" position={Position.Left} id="left-source" style={{ background: zoneInfo.color }} />
            <Handle type="target" position={Position.Top} id="top-target" style={{ background: zoneInfo.color }} />
            <Handle type="source" position={Position.Top} id="top-source" style={{ background: zoneInfo.color }} />
            <Handle type="target" position={Position.Right} id="right-target" style={{ background: zoneInfo.color }} />
            <Handle type="source" position={Position.Right} id="right-source" style={{ background: zoneInfo.color }} />
            <Handle type="target" position={Position.Bottom} id="bottom-target" style={{ background: zoneInfo.color }} />
            <Handle type="source" position={Position.Bottom} id="bottom-source" style={{ background: zoneInfo.color }} />
            <div style={{ marginBottom: '4px', display: 'flex', justifyContent: 'center' }}>
                <IconComponent size={24} color="#e2e8f0" />
            </div>
            <div style={{ fontSize: '12px', fontWeight: 'bold', color: '#e2e8f0' }}>
                {label}
            </div>
        </div>
    );
}
export default memo(InfoNode);
