import { memo } from 'react';
import { BaseEdge, getSmoothStepPath } from '@xyflow/react';

/**
 * InfoEdge - Clickable edge for the Architecture info page.
 * - Solid line for TCP
 * - Dashed line for UDP
 * - Bidirectional arrows where applicable
 * - Click to select and show info
 */
function InfoEdge({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    selected,
    markerStart,
    markerEnd,
}) {
    const { protocol } = data || {};
    const isUdp = protocol === 'UDP';

    const [edgePath] = getSmoothStepPath({
        sourceX,
        sourceY,
        targetX,
        targetY,
        sourcePosition,
        targetPosition,
        borderRadius: 16,
    });

    // Colors
    const baseColor = '#64748b';
    const selectedColor = '#22c55e';
    const strokeColor = selected ? selectedColor : baseColor;

    return (
        <>
            {/* Invisible wider path for easier clicking */}
            <path
                d={edgePath}
                fill="none"
                stroke="transparent"
                strokeWidth="20"
                style={{ cursor: 'pointer' }}
            />
            
            {/* Visible edge */}
            <BaseEdge
                id={id}
                path={edgePath}
                markerStart={markerStart}
                markerEnd={markerEnd}
                style={{
                    stroke: strokeColor,
                    strokeWidth: selected ? 2 : 1,
                    strokeDasharray: isUdp ? '5,5' : undefined,
                    transition: 'stroke 0.2s ease, stroke-width 0.2s ease',
                    cursor: 'pointer',
                }}
                interactionWidth={20}
            />
            
            {/* Glow effect when selected */}
            {selected && (
                <path
                    d={edgePath}
                    fill="none"
                    stroke={selectedColor}
                    strokeWidth="4"
                    strokeDasharray={isUdp ? '5,5' : undefined}
                    opacity="0.3"
                    style={{ pointerEvents: 'none' }}
                />
            )}
        </>
    );
}

export default memo(InfoEdge);
