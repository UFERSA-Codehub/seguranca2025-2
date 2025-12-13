import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';

const AnimationContext = createContext(null);

export function AnimationProvider({ children }) {
    const [animationState, setAnimationState] = useState({
        edgeId: null,
        isReversed: false,
        animationKey: null,
    });

    const startEdgeAnimation = useCallback((edgeId, isReversed, animationKey) => {
        setAnimationState({ edgeId, isReversed, animationKey });
    }, []);

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
