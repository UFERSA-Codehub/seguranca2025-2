import { useState, useEffect, useCallback, useRef, useMemo } from 'react';

const ANIMATION_DURATION_MS = 1000;

const DEBUG = false;
const log = (...args) => DEBUG && console.log('[Playback]', ...args);

export function usePlayback(events, options = {}) {
    const { onPlayStart, onPlayStop } = options;
    
    const eventsRef = useRef(events);
    eventsRef.current = events;
    
    const [playbackSnapshot, setPlaybackSnapshot] = useState([]);
    const [currentTraceId, setCurrentTraceId] = useState(null);
    const [isPlaying, setIsPlaying] = useState(false);
    const [isAnimating, setIsAnimating] = useState(false);
    const [animationCounter, setAnimationCounter] = useState(0);
    
    const animationTimeoutRef = useRef(null);
    const playTimeoutRef = useRef(null);
    const isPlayingRef = useRef(false);
    const isAnimatingRef = useRef(false);
    const snapshotRef = useRef([]);
    const currentTraceIdRef = useRef(currentTraceId);

    const prevEventsLengthRef = useRef(events.length);
    useEffect(() => {
        if (events.length !== prevEventsLengthRef.current) {
            log(`events prop changed: ${prevEventsLengthRef.current} -> ${events.length}, isPlaying=${isPlayingRef.current}, snapshotLen=${snapshotRef.current.length}`);
            prevEventsLengthRef.current = events.length;
        }
    }, [events.length]);

    useEffect(() => {
        log(`playbackSnapshot state changed, len=${playbackSnapshot.length}`);
        snapshotRef.current = playbackSnapshot;
    }, [playbackSnapshot]);

    useEffect(() => {
        log(`currentTraceId changed: ${currentTraceId}`);
        currentTraceIdRef.current = currentTraceId;
    }, [currentTraceId]);

    useEffect(() => {
        isAnimatingRef.current = isAnimating;
    }, [isAnimating]);

    const effectiveEvents = useMemo(() => {
        if (isPlaying || isAnimating) {
            if (snapshotRef.current.length > 0) {
                return snapshotRef.current;
            }
            if (playbackSnapshot.length > 0) {
                return playbackSnapshot;
            }
        }
        return events;
    }, [isPlaying, isAnimating, playbackSnapshot, events]);
    
    const eventsCount = effectiveEvents.length;
    const maxIndex = Math.max(0, eventsCount - 1);

    useEffect(() => {
        const source = (isPlaying || isAnimating) ? 'SNAPSHOT' : 'LIVE';
        log(`effectiveEvents source: ${source}, count=${eventsCount}, isPlaying=${isPlaying}, isAnimating=${isAnimating}`);
    }, [isPlaying, isAnimating, eventsCount]);

    const currentIndex = useMemo(() => {
        if (!currentTraceId || eventsCount === 0) return -1;
        const idx = effectiveEvents.findIndex(e => e.traceId === currentTraceId);
        return idx >= 0 ? idx : -1;
    }, [effectiveEvents, currentTraceId, eventsCount]);

    const currentEvent = useMemo(() => {
        if (currentIndex < 0 || eventsCount === 0) return null;
        return effectiveEvents[currentIndex] || null;
    }, [effectiveEvents, currentIndex, eventsCount]);

    const animationKey = useMemo(() => {
        const animating = isAnimatingRef.current || isAnimating;
        if (!animating || !currentEvent?.traceId) return null;
        const key = `${currentEvent.traceId}-${animationCounter}`;
        log(`animationKey computed: ${key}`);
        return key;
    }, [isAnimating, currentEvent?.traceId, animationCounter]);

    const clearTimeouts = useCallback(() => {
        if (animationTimeoutRef.current) {
            log('clearing animationTimeout');
            clearTimeout(animationTimeoutRef.current);
            animationTimeoutRef.current = null;
        }
        if (playTimeoutRef.current) {
            log('clearing playTimeout');
            clearTimeout(playTimeoutRef.current);
            playTimeoutRef.current = null;
        }
    }, []);

    const transitionToEvent = useCallback((traceId) => {
        log(`transitionToEvent: ${traceId}`);
        setCurrentTraceId(traceId);
        currentTraceIdRef.current = traceId;
        setAnimationCounter(c => c + 1);
    }, []);

    const startAnimation = useCallback((traceId) => {
        log(`startAnimation: ${traceId}`);
        setCurrentTraceId(traceId);
        currentTraceIdRef.current = traceId;
        setAnimationCounter(c => c + 1);
        setIsAnimating(true);
        isAnimatingRef.current = true;
    }, []);

    const stopAnimation = useCallback(() => {
        log('stopAnimation');
        setIsAnimating(false);
        isAnimatingRef.current = false;
    }, []);

    const runPlayLoopAt = useCallback((index) => {
        log(`runPlayLoopAt(${index}), isPlayingRef=${isPlayingRef.current}`);
        
        if (!isPlayingRef.current) {
            log('runPlayLoopAt: not playing, stopping animation');
            stopAnimation();
            return;
        }
        
        const snapshot = snapshotRef.current;
        log(`runPlayLoopAt: snapshot.length=${snapshot.length}`);
        
        if (snapshot.length === 0) {
            log('runPlayLoopAt: snapshot empty, stopping');
            stopAnimation();
            return;
        }

        const maxIdx = snapshot.length - 1;
        const clampedIndex = Math.max(0, Math.min(maxIdx, index));
        const event = snapshot[clampedIndex];
        
        if (!event?.traceId) {
            log('runPlayLoopAt: no event.traceId, stopping');
            stopAnimation();
            return;
        }

        log(`runPlayLoopAt: transitioning to event at index ${clampedIndex}, traceId=${event.traceId}`);
        
        transitionToEvent(event.traceId);

        animationTimeoutRef.current = setTimeout(() => {
            log(`animationTimeout fired for index ${clampedIndex}`);

            if (!isPlayingRef.current) {
                log('animationTimeout: not playing anymore, stopping animation');
                stopAnimation();
                return;
            }

            const nextIndex = clampedIndex + 1;

            if (nextIndex > maxIdx) {
                log('animationTimeout: reached end, stopping playback');
                setIsPlaying(false);
                isPlayingRef.current = false;
                stopAnimation();
                if (onPlayStop) {
                    log('animationTimeout: calling onPlayStop');
                    onPlayStop();
                }
                return;
            }

            log(`animationTimeout: immediately transitioning to next index ${nextIndex}`);
            runPlayLoopAt(nextIndex);

        }, ANIMATION_DURATION_MS);
    }, [transitionToEvent, stopAnimation, onPlayStop]);

    const play = useCallback(() => {
        log(`play() called, events.length=${events.length}`);
        
        if (events.length === 0) {
            log('play: no events, returning');
            return;
        }

        clearTimeouts();
        
        if (onPlayStart) {
            log('play: calling onPlayStart to pause updates');
            onPlayStart();
        }
        
        const snapshot = [...events];
        log(`play: captured snapshot with ${snapshot.length} events`);
        setPlaybackSnapshot(snapshot);
        snapshotRef.current = snapshot;
        
        setIsPlaying(true);
        isPlayingRef.current = true;
        setIsAnimating(true);
        isAnimatingRef.current = true;

        let startIndex = -1;
        if (currentTraceIdRef.current) {
            startIndex = snapshot.findIndex(e => e.traceId === currentTraceIdRef.current);
        }
        
        log(`play: currentTraceId=${currentTraceIdRef.current}, startIndex=${startIndex}`);
        
        if (startIndex < 0) {
            startIndex = 0;
        }
        
        if (startIndex >= snapshot.length - 1) {
            startIndex = 0;
        }

        log(`play: starting loop at index ${startIndex}`);
        runPlayLoopAt(startIndex);
    }, [events, clearTimeouts, runPlayLoopAt, onPlayStart]);

    const pause = useCallback(() => {
        log('pause() called');
        setIsPlaying(false);
        isPlayingRef.current = false;
        clearTimeouts();
        stopAnimation();
        setPlaybackSnapshot([]);
        snapshotRef.current = [];
        
        if (onPlayStop) {
            log('pause: calling onPlayStop to resume updates');
            onPlayStop();
        }
    }, [clearTimeouts, stopAnimation, onPlayStop]);

    const togglePlay = useCallback(() => {
        log(`togglePlay: isPlaying=${isPlaying}`);
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }, [isPlaying, play, pause]);

    const goToIndex = useCallback((index) => {
        if (events.length === 0) return;
        const clampedIndex = Math.max(0, Math.min(events.length - 1, index));
        const event = events[clampedIndex];
        if (event?.traceId) {
            setCurrentTraceId(event.traceId);
            currentTraceIdRef.current = event.traceId;
        }
    }, [events]);

    const stepForward = useCallback(() => {
        if (events.length === 0) return;
        
        let idx = -1;
        if (currentTraceIdRef.current) {
            idx = events.findIndex(e => e.traceId === currentTraceIdRef.current);
        }
        if (idx < 0) idx = -1;
        
        if (idx >= events.length - 1) return;
        
        clearTimeouts();
        setIsPlaying(false);
        isPlayingRef.current = false;
        setPlaybackSnapshot([]);
        snapshotRef.current = [];
        
        const nextEvent = events[idx + 1];
        if (nextEvent?.traceId) {
            startAnimation(nextEvent.traceId);
            animationTimeoutRef.current = setTimeout(() => {
                stopAnimation();
            }, ANIMATION_DURATION_MS);
        }
    }, [events, clearTimeouts, startAnimation, stopAnimation]);

    const stepBackward = useCallback(() => {
        if (events.length === 0) return;
        
        let idx = -1;
        if (currentTraceIdRef.current) {
            idx = events.findIndex(e => e.traceId === currentTraceIdRef.current);
        }
        if (idx <= 0) return;
        
        clearTimeouts();
        setIsPlaying(false);
        isPlayingRef.current = false;
        setPlaybackSnapshot([]);
        snapshotRef.current = [];
        
        const prevEvent = events[idx - 1];
        if (prevEvent?.traceId) {
            startAnimation(prevEvent.traceId);
            animationTimeoutRef.current = setTimeout(() => {
                stopAnimation();
            }, ANIMATION_DURATION_MS);
        }
    }, [events, clearTimeouts, startAnimation, stopAnimation]);

    const jumpToStart = useCallback(() => {
        if (events.length === 0) return;
        pause();
        goToIndex(0);
    }, [events.length, pause, goToIndex]);

    const jumpToEnd = useCallback(() => {
        if (events.length === 0) return;
        pause();
        goToIndex(events.length - 1);
    }, [events.length, pause, goToIndex]);

    const seekTo = useCallback((index) => {
        if (events.length === 0) return;
        pause();
        goToIndex(index);
    }, [events.length, pause, goToIndex]);

    useEffect(() => {
        if (events.length > 0 && currentTraceId === null) {
            const oldestEvent = events[0];
            if (oldestEvent?.traceId) {
                log(`initializing to oldest event: ${oldestEvent.traceId}`);
                setCurrentTraceId(oldestEvent.traceId);
                currentTraceIdRef.current = oldestEvent.traceId;
            }
        }
    }, [events.length, currentTraceId, events]);

    useEffect(() => {
        return () => {
            clearTimeouts();
            isPlayingRef.current = false;
            if (onPlayStop && isPlayingRef.current) {
                onPlayStop();
            }
        };
    }, [clearTimeouts, onPlayStop]);

    const liveMaxIndex = Math.max(0, events.length - 1);

    return {
        currentIndex: currentIndex < 0 ? 0 : currentIndex,
        currentEvent,
        isPlaying,
        isAnimating,
        animationKey,
        play,
        pause,
        togglePlay,
        stepForward,
        stepBackward,
        jumpToStart,
        jumpToEnd,
        seekTo,
        maxIndex: liveMaxIndex,
    };
}
