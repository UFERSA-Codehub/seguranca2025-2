import { useState, useEffect, useCallback, useRef, useMemo } from 'react';

const ANIMATION_DURATION_MS = 2000;

export function usePlayback(events) {
    const [currentTraceId, setCurrentTraceId] = useState(null);
    const [isPlaying, setIsPlaying] = useState(false);
    const [isAnimating, setIsAnimating] = useState(false);
    const animationTimeoutRef = useRef(null);
    const playIntervalRef = useRef(null);

    const eventsCount = events.length;

    const currentIndex = useMemo(() => {
        if (!currentTraceId || eventsCount === 0) return -1;
        return events.findIndex((e) => e.traceId === currentTraceId);
    }, [events, currentTraceId, eventsCount]);

    const currentEvent = useMemo(() => {
        if (currentIndex < 0 || eventsCount === 0) return null;
        return events[currentIndex] || null;
    }, [events, currentIndex, eventsCount]);

    const maxIndex = Math.max(0, eventsCount - 1);

    const clearTimeouts = useCallback(() => {
        if (animationTimeoutRef.current) {
            clearTimeout(animationTimeoutRef.current);
            animationTimeoutRef.current = null;
        }
        if (playIntervalRef.current) {
            clearTimeout(playIntervalRef.current);
            playIntervalRef.current = null;
        }
    }, []);

    const startAnimation = useCallback((onComplete) => {
        setIsAnimating(true);
        animationTimeoutRef.current = setTimeout(() => {
            setIsAnimating(false);
            if (onComplete) onComplete();
        }, ANIMATION_DURATION_MS);
    }, []);

    const goToIndex = useCallback((index, animate = true) => {
        if (eventsCount === 0) return;
        const clampedIndex = Math.max(0, Math.min(maxIndex, index));
        const event = events[clampedIndex];
        if (event) {
            setCurrentTraceId(event.traceId);
            if (animate) {
                startAnimation(null);
            }
        }
    }, [eventsCount, maxIndex, events, startAnimation]);

    const advanceToNext = useCallback(() => {
        if (currentIndex <= 0) {
            setIsPlaying(false);
            return false;
        }
        goToIndex(currentIndex - 1, true);
        return true;
    }, [currentIndex, goToIndex]);

    const play = useCallback(() => {
        if (eventsCount === 0) return;

        clearTimeouts();
        setIsPlaying(true);

        if (currentIndex < 0) {
            goToIndex(maxIndex, true);
        }

        const scheduleNext = () => {
            playIntervalRef.current = setTimeout(() => {
                const canContinue = advanceToNext();
                if (canContinue) {
                    startAnimation(() => scheduleNext());
                }
            }, ANIMATION_DURATION_MS);
        };

        startAnimation(() => scheduleNext());
    }, [eventsCount, currentIndex, maxIndex, clearTimeouts, goToIndex, startAnimation, advanceToNext]);

    const pause = useCallback(() => {
        setIsPlaying(false);
        clearTimeouts();
        setIsAnimating(false);
    }, [clearTimeouts]);

    const togglePlay = useCallback(() => {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }, [isPlaying, play, pause]);

    const stepForward = useCallback(() => {
        if (eventsCount === 0 || currentIndex <= 0) return;
        clearTimeouts();
        setIsPlaying(false);
        goToIndex(currentIndex - 1, true);
    }, [eventsCount, currentIndex, clearTimeouts, goToIndex]);

    const stepBackward = useCallback(() => {
        if (eventsCount === 0 || currentIndex >= maxIndex) return;
        clearTimeouts();
        setIsPlaying(false);
        goToIndex(currentIndex + 1, true);
    }, [eventsCount, currentIndex, maxIndex, clearTimeouts, goToIndex]);

    const jumpToStart = useCallback(() => {
        if (eventsCount === 0) return;
        clearTimeouts();
        setIsPlaying(false);
        setIsAnimating(false);
        goToIndex(maxIndex, false);
    }, [eventsCount, maxIndex, clearTimeouts, goToIndex]);

    const jumpToEnd = useCallback(() => {
        if (eventsCount === 0) return;
        clearTimeouts();
        setIsPlaying(false);
        setIsAnimating(false);
        goToIndex(0, false);
    }, [eventsCount, clearTimeouts, goToIndex]);

    const seekTo = useCallback((index) => {
        if (eventsCount === 0) return;
        clearTimeouts();
        setIsPlaying(false);
        setIsAnimating(false);
        goToIndex(index, false);
    }, [eventsCount, clearTimeouts, goToIndex]);

    useEffect(() => {
        if (eventsCount > 0 && currentTraceId === null) {
            setCurrentTraceId(events[maxIndex].traceId);
        }
    }, [eventsCount, currentTraceId, events, maxIndex]);

    useEffect(() => {
        return clearTimeouts;
    }, [clearTimeouts]);

    return {
        currentTraceId,
        currentIndex: currentIndex < 0 ? 0 : currentIndex,
        currentEvent,
        isPlaying,
        isAnimating,
        play,
        pause,
        togglePlay,
        stepForward,
        stepBackward,
        jumpToStart,
        jumpToEnd,
        seekTo,
        maxIndex,
    };
}
