import { memo } from 'react';
import { BaseEdge, getSmoothStepPath } from '@xyflow/react';

const ANIMATION_DURATION = '2s';

function AnimatedEdge({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    markerEnd,
}) {
    const [edgePath] = getSmoothStepPath({
        sourceX,
        sourceY,
        targetX,
        targetY,
        sourcePosition,
        targetPosition,
        borderRadius: 16,
    });

    const isUdp = data?.protocol === 'UDP';
    const isAnimating = data?.isAnimating;

    const baseColor = '#64748b';
    const activeColor = '#22c55e';
    const strokeColor = isAnimating ? activeColor : baseColor;

    return (
        <>
            <BaseEdge
                id={id}
                path={edgePath}
                markerEnd={markerEnd}
                style={{
                    stroke: strokeColor,
                    strokeWidth: isAnimating ? 2 : 1,
                    strokeDasharray: isUdp ? '5,5' : undefined,
                    transition: 'stroke 0.3s ease, stroke-width 0.3s ease',
                }}
            />
            {isAnimating && (
                <g key={`anim-${id}-${Date.now()}`}>
                    <circle r="6" fill={activeColor}>
                        <animateMotion
                            dur={ANIMATION_DURATION}
                            path={edgePath}
                            fill="freeze"
                            keyPoints="0;1"
                            keyTimes="0;1"
                            calcMode="linear"
                        />
                    </circle>
                    <circle r="6" fill={activeColor} opacity="0.5">
                        <animateMotion
                            dur={ANIMATION_DURATION}
                            path={edgePath}
                            fill="freeze"
                            keyPoints="0;1"
                            keyTimes="0;1"
                            calcMode="linear"
                        />
                        <animate
                            attributeName="r"
                            values="6;12;6"
                            dur="0.5s"
                            repeatCount="indefinite"
                        />
                        <animate
                            attributeName="opacity"
                            values="0.5;0;0.5"
                            dur="0.5s"
                            repeatCount="indefinite"
                        />
                    </circle>
                </g>
            )}
            {isAnimating && (
                <path
                    d={edgePath}
                    fill="none"
                    stroke={activeColor}
                    strokeWidth="4"
                    strokeDasharray={isUdp ? '5,5' : undefined}
                    opacity="0.3"
                >
                    <animate
                        attributeName="opacity"
                        values="0.3;0.6;0.3"
                        dur="0.5s"
                        repeatCount="indefinite"
                    />
                </path>
            )}
        </>
    );
}

export default memo(AnimatedEdge);
