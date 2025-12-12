import { useState, useEffect, useCallback, useRef, useMemo } from 'react';

const ANIMATION_DURATION_MS = 1000;

// Debug logging - set to false to disable
const DEBUG = false;
const log = (...args) => DEBUG && console.log('[Playback]', ...args);

/**
 * Hook para controle de playback de eventos.
 * 
 * Usa snapshot de eventos para evitar que novos eventos via WebSocket
 * interfiram com a animacao em andamento. O snapshot e capturado quando
 * o playback inicia e atualizado apenas quando para ou quando usuario
 * faz seek manual.
 * 
 * IMPORTANTE: Durante auto-play, isAnimating permanece TRUE o tempo todo.
 * A transicao entre eventos acontece apenas mudando currentTraceId e animationKey.
 * Isso evita o "flicker" causado por desmontar/remontar o componente de animacao.
 * 
 * IMPORTANTE: Usamos refs para isPlaying e isAnimating durante playback loop
 * para evitar issues de stale closures e state timing.
 * 
 * NOVO: Aceita callbacks onPlayStart e onPlayStop para pausar/resumir
 * atualizacoes do TraceContext, isolando completamente a animacao.
 * 
 * @param {Array} events - Lista de eventos para playback
 * @param {Object} options - Opcoes opcionais
 * @param {Function} options.onPlayStart - Callback chamado quando playback inicia (para pausar updates)
 * @param {Function} options.onPlayStop - Callback chamado quando playback para (para resumir updates)
 */
export function usePlayback(events, options = {}) {
    const { onPlayStart, onPlayStop } = options;
    
    // Use ref for events to avoid re-renders when new events arrive during playback
    const eventsRef = useRef(events);
    eventsRef.current = events;
    
    // Snapshot de eventos usado durante playback (isolado de novas chegadas)
    const [playbackSnapshot, setPlaybackSnapshot] = useState([]);
    // TraceId do evento atual
    const [currentTraceId, setCurrentTraceId] = useState(null);
    const [isPlaying, setIsPlaying] = useState(false);
    const [isAnimating, setIsAnimating] = useState(false);
    // Counter para forcar restart de animacao mesmo para mesmo evento
    const [animationCounter, setAnimationCounter] = useState(0);
    
    const animationTimeoutRef = useRef(null);
    const playTimeoutRef = useRef(null);
    const isPlayingRef = useRef(false);
    const isAnimatingRef = useRef(false);
    // Ref para snapshot - usado no play loop para evitar closures stale
    const snapshotRef = useRef([]);
    const currentTraceIdRef = useRef(currentTraceId);

    // Log when events prop changes (only in debug mode)
    const prevEventsLengthRef = useRef(events.length);
    useEffect(() => {
        if (events.length !== prevEventsLengthRef.current) {
            log(`events prop changed: ${prevEventsLengthRef.current} -> ${events.length}, isPlaying=${isPlayingRef.current}, snapshotLen=${snapshotRef.current.length}`);
            prevEventsLengthRef.current = events.length;
        }
    }, [events.length]);

    // Manter refs sincronizados
    useEffect(() => {
        log(`playbackSnapshot state changed, len=${playbackSnapshot.length}`);
        snapshotRef.current = playbackSnapshot;
    }, [playbackSnapshot]);

    useEffect(() => {
        log(`currentTraceId changed: ${currentTraceId}`);
        currentTraceIdRef.current = currentTraceId;
    }, [currentTraceId]);

    // Sync isAnimating ref with state
    useEffect(() => {
        isAnimatingRef.current = isAnimating;
    }, [isAnimating]);

    // Effective events: use snapshot during playback to avoid interruptions
    // IMPORTANT: During playback, we ONLY use snapshot to ensure stability
    // We compare lengths to determine if snapshot is valid
    const effectiveEvents = useMemo(() => {
        // When playing or animating, prefer the snapshot if it has events
        if (isPlaying || isAnimating) {
            // Use ref first as it's set synchronously, then fall back to state
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

    // Log effective events source
    useEffect(() => {
        const source = (isPlaying || isAnimating) ? 'SNAPSHOT' : 'LIVE';
        log(`effectiveEvents source: ${source}, count=${eventsCount}, isPlaying=${isPlaying}, isAnimating=${isAnimating}`);
    }, [isPlaying, isAnimating, eventsCount]);

    // Calcular indice atual baseado no traceId - memoized for stability
    const currentIndex = useMemo(() => {
        if (!currentTraceId || eventsCount === 0) return -1;
        const idx = effectiveEvents.findIndex(e => e.traceId === currentTraceId);
        return idx >= 0 ? idx : -1;
    }, [effectiveEvents, currentTraceId, eventsCount]);

    // Current event - use stable reference by comparing traceId
    const currentEvent = useMemo(() => {
        if (currentIndex < 0 || eventsCount === 0) return null;
        return effectiveEvents[currentIndex] || null;
    }, [effectiveEvents, currentIndex, eventsCount]);

    // Chave unica de animacao que muda a cada trigger
    // Use isAnimatingRef to avoid state timing issues
    const animationKey = useMemo(() => {
        // Check both ref and state for maximum reliability
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

    // Transicionar para proximo evento SEM desligar isAnimating
    // Apenas muda o traceId e incrementa o counter para forcar nova animacao
    const transitionToEvent = useCallback((traceId) => {
        log(`transitionToEvent: ${traceId}`);
        setCurrentTraceId(traceId);
        currentTraceIdRef.current = traceId;
        setAnimationCounter(c => c + 1);
        // NAO muda isAnimating - permanece true durante playback
    }, []);

    // Iniciar animacao para um evento (usado para inicio de playback e steps manuais)
    const startAnimation = useCallback((traceId) => {
        log(`startAnimation: ${traceId}`);
        setCurrentTraceId(traceId);
        currentTraceIdRef.current = traceId;
        setAnimationCounter(c => c + 1);
        setIsAnimating(true);
        isAnimatingRef.current = true;
    }, []);

    // Parar animacao completamente
    const stopAnimation = useCallback(() => {
        log('stopAnimation');
        setIsAnimating(false);
        isAnimatingRef.current = false;
    }, []);

    // Loop de playback: eventos ordenados oldest-first (index 0 = oldest)
    // Forward = index crescente (older -> newer)
    // IMPORTANTE: Durante o loop, isAnimating permanece TRUE
    const runPlayLoopAt = useCallback((index) => {
        log(`runPlayLoopAt(${index}), isPlayingRef=${isPlayingRef.current}`);
        
        if (!isPlayingRef.current) {
            log('runPlayLoopAt: not playing, stopping animation');
            stopAnimation();
            return;
        }
        
        // Usar snapshot ref para evitar closure stale
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
        
        // Transicionar para este evento (sem desligar isAnimating)
        transitionToEvent(event.traceId);

        animationTimeoutRef.current = setTimeout(() => {
            log(`animationTimeout fired for index ${clampedIndex}`);
            
            // NAO desliga isAnimating aqui - apenas agenda o proximo

            if (!isPlayingRef.current) {
                log('animationTimeout: not playing anymore, stopping animation');
                stopAnimation();
                return;
            }

            // Forward = movendo para eventos mais novos = index crescente
            const nextIndex = clampedIndex + 1;

            if (nextIndex > maxIdx) {
                log('animationTimeout: reached end, stopping playback');
                setIsPlaying(false);
                isPlayingRef.current = false;
                stopAnimation();
                // Resume updates when playback ends naturally
                if (onPlayStop) {
                    log('animationTimeout: calling onPlayStop');
                    onPlayStop();
                }
                return;
            }

            log(`animationTimeout: immediately transitioning to next index ${nextIndex}`);
            // Transicionar IMEDIATAMENTE para o proximo evento (sem delay)
            // O delay de 100ms era a causa do flicker
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
        
        // Pause trace updates BEFORE starting playback
        // This prevents new events from arriving and causing re-renders
        if (onPlayStart) {
            log('play: calling onPlayStart to pause updates');
            onPlayStart();
        }
        
        // Capturar snapshot dos eventos atuais - isso isola o playback
        // de novos eventos que chegarem durante a animacao
        const snapshot = [...events];
        log(`play: captured snapshot with ${snapshot.length} events`);
        setPlaybackSnapshot(snapshot);
        snapshotRef.current = snapshot;
        
        setIsPlaying(true);
        isPlayingRef.current = true;
        setIsAnimating(true);
        isAnimatingRef.current = true;

        // Determinar indice inicial baseado no traceId atual
        let startIndex = -1;
        if (currentTraceIdRef.current) {
            startIndex = snapshot.findIndex(e => e.traceId === currentTraceIdRef.current);
        }
        
        log(`play: currentTraceId=${currentTraceIdRef.current}, startIndex=${startIndex}`);
        
        // Se nao tem indice valido, comecar do mais antigo (index 0)
        if (startIndex < 0) {
            startIndex = 0;
        }
        
        // Se ja esta no evento mais novo, reiniciar do mais antigo
        if (startIndex >= snapshot.length - 1) {
            startIndex = 0;
        }

        log(`play: starting loop at index ${startIndex}`);
        // Iniciar loop de playback
        runPlayLoopAt(startIndex);
    }, [events, clearTimeouts, runPlayLoopAt, onPlayStart]);

    const pause = useCallback(() => {
        log('pause() called');
        setIsPlaying(false);
        isPlayingRef.current = false;
        clearTimeouts();
        stopAnimation();
        // Ao pausar, limpar snapshot para voltar a usar eventos live
        setPlaybackSnapshot([]);
        snapshotRef.current = [];
        
        // Resume trace updates when pausing
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

    // Ir para indice especifico (usa eventos live, nao snapshot)
    const goToIndex = useCallback((index) => {
        if (events.length === 0) return;
        const clampedIndex = Math.max(0, Math.min(events.length - 1, index));
        const event = events[clampedIndex];
        if (event?.traceId) {
            setCurrentTraceId(event.traceId);
            currentTraceIdRef.current = event.traceId;
        }
    }, [events]);

    // Step forward = mover para evento mais novo = aumentar index
    const stepForward = useCallback(() => {
        if (events.length === 0) return;
        
        // Encontrar indice atual nos eventos live
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

    // Step backward = mover para evento mais antigo = diminuir index
    const stepBackward = useCallback(() => {
        if (events.length === 0) return;
        
        // Encontrar indice atual nos eventos live
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

    // Pular para inicio = evento mais antigo = index 0
    const jumpToStart = useCallback(() => {
        if (events.length === 0) return;
        pause();
        goToIndex(0);
    }, [events.length, pause, goToIndex]);

    // Pular para fim = evento mais novo = maxIndex
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

    // Inicializar para evento mais antigo quando eventos chegam pela primeira vez
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

    // Cleanup ao desmontar
    useEffect(() => {
        return () => {
            clearTimeouts();
            isPlayingRef.current = false;
            // Ensure updates are resumed on unmount
            if (onPlayStop && isPlayingRef.current) {
                onPlayStop();
            }
        };
    }, [clearTimeouts, onPlayStop]);

    // maxIndex para UI deve refletir eventos live (para slider de seek)
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
