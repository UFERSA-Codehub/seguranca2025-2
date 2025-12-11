import { memo } from 'react';
import {
    SkipBack,
    ChevronLeft,
    Play,
    Pause,
    ChevronRight,
    SkipForward,
} from 'lucide-react';

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
}) {
    const totalEvents = maxIndex + 1;
    const displayIndex = maxIndex - currentIndex + 1;

    const handleScrubberChange = (e) => {
        const value = parseInt(e.target.value, 10);
        seekTo(maxIndex - value);
    };

    const scrubberValue = maxIndex - currentIndex;

    const canStepBackward = currentIndex < maxIndex;
    const canStepForward = currentIndex > 0;

    return (
        <div className="playback-hud">
            <div className="playback-row">
                <div className="transport-controls">
                    <button
                        className="transport-btn"
                        onClick={jumpToStart}
                        title="Jump to oldest"
                        disabled={!canStepBackward}
                    >
                        <SkipBack size={18} />
                    </button>
                    <button
                        className="transport-btn"
                        onClick={stepBackward}
                        title="Step backward"
                        disabled={!canStepBackward || isAnimating}
                    >
                        <ChevronLeft size={18} />
                    </button>
                    <button
                        className="transport-btn play-pause"
                        onClick={togglePlay}
                        title={isPlaying ? 'Pause' : 'Play'}
                        disabled={totalEvents === 0}
                    >
                        {isPlaying ? <Pause size={20} /> : <Play size={20} />}
                    </button>
                    <button
                        className="transport-btn"
                        onClick={stepForward}
                        title="Step forward"
                        disabled={!canStepForward || isAnimating}
                    >
                        <ChevronRight size={18} />
                    </button>
                    <button
                        className="transport-btn"
                        onClick={jumpToEnd}
                        title="Jump to latest"
                        disabled={!canStepForward}
                    >
                        <SkipForward size={18} />
                    </button>
                </div>
            </div>

            <div className="playback-row timeline-row">
                <input
                    type="range"
                    className="timeline-scrubber"
                    min={0}
                    max={maxIndex}
                    value={scrubberValue}
                    onChange={handleScrubberChange}
                    disabled={totalEvents === 0}
                />
                <span className="event-counter">
                    {totalEvents > 0 ? `${displayIndex} / ${totalEvents}` : '0 / 0'}
                </span>
            </div>
        </div>
    );
}

export default memo(PlaybackControls);
