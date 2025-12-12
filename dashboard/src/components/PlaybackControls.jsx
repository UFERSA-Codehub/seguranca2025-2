import { memo } from 'react';
import {
    SkipBack,
    ChevronLeft,
    Play,
    Pause,
    ChevronRight,
    SkipForward,
    Download,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Slider } from '@/components/ui/slider';

/**
 * Playback controls HUD with transport buttons and timeline scrubber.
 * Uses shadcn Button and Slider components for consistent styling.
 */
function PlaybackControls({
    isPlaying,
    isAnimating,
    togglePlay,
    currentIndex,
    maxIndex,
    stepForward,
    stepBackward,
    jumpToStart,
    jumpToEnd,
    seekTo,
    isPaused,
    bufferedCount,
}) {
    const totalEvents = maxIndex + 1;
    // Display: index 0 = event 1, index max = event max+1
    const displayIndex = currentIndex + 1;

    const handleScrubberChange = (values) => {
        // Slider returns an array of values
        if (values && values.length > 0) {
            seekTo(values[0]);
        }
    };

    // Can step backward = go to older event = decrease index (must be > 0)
    const canStepBackward = currentIndex > 0;
    // Can step forward = go to newer event = increase index (must be < max)
    const canStepForward = currentIndex < maxIndex;

    return (
        <div className="playback-hud">
            <div className="playback-row">
                <div className="transport-controls">
                    <Button
                        variant="secondary"
                        size="icon"
                        onClick={jumpToStart}
                        title="Ir para o mais antigo"
                        disabled={!canStepBackward}
                        className="h-9 w-9 bg-slate-700 hover:bg-slate-600 border-none"
                    >
                        <SkipBack className="h-4 w-4" />
                    </Button>
                    <Button
                        variant="secondary"
                        size="icon"
                        onClick={stepBackward}
                        title="Retroceder (mais antigo)"
                        disabled={!canStepBackward || isAnimating}
                        className="h-9 w-9 bg-slate-700 hover:bg-slate-600 border-none"
                    >
                        <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <Button
                        variant="default"
                        size="icon"
                        onClick={togglePlay}
                        title={isPlaying ? 'Pausar' : 'Reproduzir'}
                        disabled={totalEvents === 0}
                        className="h-11 w-11 bg-green-600 hover:bg-green-500 border-none"
                    >
                        {isPlaying ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5" />}
                    </Button>
                    <Button
                        variant="secondary"
                        size="icon"
                        onClick={stepForward}
                        title="Avançar (mais recente)"
                        disabled={!canStepForward || isAnimating}
                        className="h-9 w-9 bg-slate-700 hover:bg-slate-600 border-none"
                    >
                        <ChevronRight className="h-4 w-4" />
                    </Button>
                    <Button
                        variant="secondary"
                        size="icon"
                        onClick={jumpToEnd}
                        title="Ir para o mais recente"
                        disabled={!canStepForward}
                        className="h-9 w-9 bg-slate-700 hover:bg-slate-600 border-none"
                    >
                        <SkipForward className="h-4 w-4" />
                    </Button>
                </div>
            </div>

            <div className="playback-row timeline-row">
                <Slider
                    min={0}
                    max={maxIndex > 0 ? maxIndex : 1}
                    value={[currentIndex]}
                    onValueChange={handleScrubberChange}
                    disabled={totalEvents === 0}
                    className="flex-1 [&_[data-slot=slider-track]]:bg-slate-700 [&_[data-slot=slider-range]]:bg-indigo-500 [&_[data-slot=slider-thumb]]:border-indigo-500 [&_[data-slot=slider-thumb]]:bg-white"
                />
                <span className="event-counter">
                    {totalEvents > 0 ? `${displayIndex} / ${totalEvents}` : '0 / 0'}
                </span>
                {isPaused && bufferedCount > 0 && (
                    <span className="buffered-indicator" title="Eventos em buffer (aguardando fim da reprodução)">
                        <Download className="h-3.5 w-3.5" />
                        +{bufferedCount}
                    </span>
                )}
            </div>
        </div>
    );
}

export default memo(PlaybackControls);
