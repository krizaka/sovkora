"use client";

import * as React from "react";
import { useQuery } from "@krizaka/query";
import { PauseIcon, PlayIcon } from "@krizaka/icons";
import { IconButton } from "@krizaka/ui/button";
import { cn } from "@krizaka/ui/cn";
import { useTranslation } from "@/core/context/LocaleContext";

const BARS = 64;

/** The loudness of `BARS` slices of a decoded sound, 0..1 — the waveform of the file itself, never a decoration. */
async function peaksOf(src: string, signal: AbortSignal): Promise<number[]> {
  const data = await (await fetch(src, { signal })).arrayBuffer();
  const Ctx =
    globalThis.AudioContext ??
    (globalThis as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
  const ctx = new Ctx();
  try {
    const channel = (await ctx.decodeAudioData(data)).getChannelData(0);
    const size = Math.max(1, Math.floor(channel.length / BARS));
    const peaks = Array.from({ length: BARS }, (_, i) => {
      let max = 0;
      for (let j = i * size; j < Math.min((i + 1) * size, channel.length); j++)
        max = Math.max(max, Math.abs(channel[j]));
      return max;
    });
    const top = Math.max(...peaks, 0.001);
    return peaks.map((p) => p / top);
  } finally {
    void ctx.close();
  }
}

interface AudioWaveformProps {
  /** The sound's URL (a generated asset). */
  src: string;
  /** What it is, for the play button's name ("Voice-over"). */
  label: string;
  className?: string;
}

/**
 * A generated sound as what it is: its waveform, drawn from the decoded file, which fills in the accent as it plays,
 * with one play / pause button (the @krizaka/ui icon button). Falls back to the browser's player when the file cannot
 * be decoded.
 */
export function AudioWaveform({ src, label, className }: Readonly<AudioWaveformProps>) {
  const { t } = useTranslation();
  const audio = React.useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = React.useState(false);
  const [progress, setProgress] = React.useState(0);

  // One decode per source; a new source cancels the one in flight (the query's signal) and shows its own result.
  const decoded = useQuery({
    queryKey: ["audio-peaks", src],
    queryFn: ({ signal }) => peaksOf(src, signal),
    retry: false,
    staleTime: Infinity,
  });
  const peaks = decoded.data ?? null;
  const failed = decoded.isError;

  if (failed) {
    return (
      <audio src={src} controls className={cn("w-full", className)}>
        <track kind="captions" />
      </audio>
    );
  }

  const toggle = () => {
    const el = audio.current;
    if (!el) return;
    if (el.paused) void el.play();
    else el.pause();
  };

  return (
    <div className={cn("flex items-center gap-3", className)} data-testid="audio-waveform">
      <IconButton
        variant="primary"
        shape="pill"
        onClick={toggle}
        label={`${playing ? t.showcase.media.pause : t.showcase.media.play} — ${label}`}
      >
        {playing ? <PauseIcon size={18} /> : <PlayIcon size={18} />}
      </IconButton>
      <svg viewBox={`0 0 ${BARS * 4} 40`} preserveAspectRatio="none" className="h-10 flex-1" aria-hidden="true">
        {(peaks ?? Array.from({ length: BARS }, () => 0.15)).map((p, i) => {
          const h = Math.max(2, p * 36);
          return (
            <rect
              key={i}
              x={i * 4 + 0.5}
              y={20 - h / 2}
              width={2.5}
              height={h}
              rx={1.25}
              className={i / BARS < progress ? "fill-accent" : "fill-border-strong"}
            />
          );
        })}
      </svg>
      <audio
        ref={audio}
        src={src}
        preload="auto"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => {
          setPlaying(false);
          setProgress(1);
        }}
        onTimeUpdate={(e) =>
          setProgress(e.currentTarget.duration ? e.currentTarget.currentTime / e.currentTarget.duration : 0)
        }
      >
        <track kind="captions" />
      </audio>
    </div>
  );
}
