import React, { memo, useRef, useState, useEffect } from 'react';
import { BaseEdge, getSmoothStepPath } from '@xyflow/react';
import { useEdgeAnimation } from '@/hooks/useAnimationContext';

// Match usePlayback.js ANIMATION_DURATION_MS (2000ms = 2s)
const ANIMATION_DURATION_MS = 1000;

// Envelope icon as a simple SVG group
const EnvelopeIcon = ({ color }) => (
    <g>
        <rect x="-10" y="-7" width="20" height="14" rx="2" fill={color} stroke="#fff" strokeWidth="1" />
        <path d="M-10 -5 L0 2 L10 -5" fill="none" stroke="#fff" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </g>
);

/**
 * Calcula posicao ao longo de um path SVG
 * @param pathElement - elemento SVGPathElement
 * @param progress - 0 a 1
 * @returns {x, y, angle}
 */
function getPointAlongPath(pathElement, progress) {
    if (!pathElement) return { x: 0, y: 0, angle: 0 };
    
    const length = pathElement.getTotalLength();
    const point = pathElement.getPointAtLength(progress * length);
    
    // Calcula angulo baseado na direcao do path
    const delta = 0.01;
    const p1 = pathElement.getPointAtLength(Math.max(0, progress - delta) * length);
    const p2 = pathElement.getPointAtLength(Math.min(1, progress + delta) * length);
    const angle = Math.atan2(p2.y - p1.y, p2.x - p1.x) * (180 / Math.PI);
    
    return { x: point.x, y: point.y, angle };
}

/**
 * Componente de animacao usando requestAnimationFrame para suavidade.
 * Usa easing cubic-bezier para movimento mais natural.
 */
function AnimatedEnvelope({ pathRef, isReversed, activeColor, animationKey }) {
    const [position, setPosition] = useState(null);
    const startTimeRef = useRef(null);
    const animationFrameRef = useRef(null);
    
    useEffect(() => {
        if (!pathRef.current) return;
        
        // Calcular posicao inicial IMEDIATAMENTE antes de iniciar animacao
        // Isso evita o flash no canto superior esquerdo (0,0)
        const initialProgress = isReversed ? 1 : 0;
        const initialPos = getPointAlongPath(pathRef.current, initialProgress);
        setPosition(initialPos);
        
        startTimeRef.current = performance.now();
        
        // Easing function: ease-out cubic for smooth deceleration
        const easeOutCubic = (t) => 1 - Math.pow(1 - t, 3);
        
        const animate = (currentTime) => {
            const elapsed = currentTime - startTimeRef.current;
            const linearProgress = Math.min(elapsed / ANIMATION_DURATION_MS, 1);
            
            // Aplica easing para movimento mais natural
            let progress = easeOutCubic(linearProgress);
            
            // Se reverso, calcula posicao do fim para o inicio
            const pathProgress = isReversed ? 1 - progress : progress;
            
            const pos = getPointAlongPath(pathRef.current, pathProgress);
            setPosition(pos);
            
            if (linearProgress < 1) {
                animationFrameRef.current = requestAnimationFrame(animate);
            }
        };
        
        // Iniciar animacao
        animationFrameRef.current = requestAnimationFrame(animate);
        
        return () => {
            if (animationFrameRef.current) {
                cancelAnimationFrame(animationFrameRef.current);
            }
        };
    }, [animationKey, isReversed, pathRef]);
    
    // Nao renderizar ate ter posicao valida
    if (!position) return null;
    
    return (
        <g transform={`translate(${position.x}, ${position.y}) rotate(${position.angle})`}>
            {/* Glow effect behind envelope */}
            <circle r="14" fill={activeColor} opacity="0.3">
                <animate
                    attributeName="r"
                    values="12;16;12"
                    dur="0.4s"
                    repeatCount="indefinite"
                />
                <animate
                    attributeName="opacity"
                    values="0.2;0.4;0.2"
                    dur="0.4s"
                    repeatCount="indefinite"
                />
            </circle>
            <EnvelopeIcon color={activeColor} />
        </g>
    );
}

function AnimatedEdge({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    markerStart,
    markerEnd,
}) {
    const pathRef = useRef(null);
    // Força re-render quando pathRef é atribuído
    const [pathReady, setPathReady] = useState(false);
    
    // Obter estado de animacao do contexto (isolado de atualizacoes de edges)
    const { isAnimating, isReversed, animationKey } = useEdgeAnimation(id);
    
    const [edgePath] = getSmoothStepPath({
        sourceX,
        sourceY,
        targetX,
        targetY,
        sourcePosition,
        targetPosition,
        borderRadius: 16,
    });

    // Callback ref para detectar quando path está pronto
    const setPathRef = (el) => {
        pathRef.current = el;
        if (el && !pathReady) {
            setPathReady(true);
        }
    };

    const isUdp = data?.protocol === 'UDP';

    const baseColor = '#64748b';
    const activeColor = '#22c55e';
    const strokeColor = isAnimating ? activeColor : baseColor;

    return (
        <>
            {/* Path invisivel para calculos de posicao */}
            <path
                ref={setPathRef}
                d={edgePath}
                fill="none"
                stroke="transparent"
                strokeWidth="0"
            />
            
            {/* Edge principal */}
            <BaseEdge
                id={id}
                path={edgePath}
                markerStart={markerStart}
                markerEnd={markerEnd}
                style={{
                    stroke: strokeColor,
                    strokeWidth: isAnimating ? 2 : 1,
                    strokeDasharray: isUdp ? '5,5' : undefined,
                    transition: 'stroke 0.3s ease, stroke-width 0.3s ease',
                }}
            />
            
            {/* Glow pulsante no path durante animacao */}
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
                        values="0.2;0.5;0.2"
                        dur="0.6s"
                        repeatCount="indefinite"
                    />
                </path>
            )}
            
            {/* Envelope animado */}
            {isAnimating && animationKey && pathReady && (
                <AnimatedEnvelope
                    key={animationKey}
                    pathRef={pathRef}
                    isReversed={isReversed}
                    activeColor={activeColor}
                    animationKey={animationKey}
                />
            )}
        </>
    );
}

export default memo(AnimatedEdge);
