import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';

/**
 * Context para gerenciar estado de animacao de edges.
 * 
 * Isola o estado de animacao do React Flow para evitar que
 * atualizacoes de edges (quando novos eventos chegam) interrompam
 * a animacao em andamento.
 */
const AnimationContext = createContext(null);

export function AnimationProvider({ children }) {
    // Estado de animacao atual
    const [animationState, setAnimationState] = useState({
        edgeId: null,
        isReversed: false,
        animationKey: null,
    });

    // Iniciar animacao em um edge
    const startEdgeAnimation = useCallback((edgeId, isReversed, animationKey) => {
        setAnimationState({ edgeId, isReversed, animationKey });
    }, []);

    // Parar animacao
    const stopEdgeAnimation = useCallback(() => {
        setAnimationState({ edgeId: null, isReversed: false, animationKey: null });
    }, []);

    const value = useMemo(() => ({
        animationState,
        startEdgeAnimation,
        stopEdgeAnimation,
    }), [animationState, startEdgeAnimation, stopEdgeAnimation]);

    return (
        <AnimationContext.Provider value={value}>
            {children}
        </AnimationContext.Provider>
    );
}

export function useAnimationContext() {
    const context = useContext(AnimationContext);
    if (!context) {
        throw new Error('useAnimationContext must be used within AnimationProvider');
    }
    return context;
}

/**
 * Hook para verificar se um edge especifico esta animando.
 * Retorna estado de animacao apenas se o edgeId corresponder.
 */
export function useEdgeAnimation(edgeId) {
    const { animationState } = useAnimationContext();
    
    if (animationState.edgeId !== edgeId) {
        return { isAnimating: false, isReversed: false, animationKey: null };
    }
    
    return {
        isAnimating: true,
        isReversed: animationState.isReversed,
        animationKey: animationState.animationKey,
    };
}
