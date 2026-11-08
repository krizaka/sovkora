"use client";

import * as React from "react";
import { VideoSources } from "@/core/clips/VideoSources";
import { useOnScreen, useStillMotion } from "@/core/clips/usePlayback";

interface Props {
  webm: string;
  mp4: string;
  poster: string;
  label: string;
}

/**
 * The home film in a 16:9 box reserved before anything loads (no layout shift; the poster is the first paint). It plays
 * muted while it is on screen and pauses when it leaves; a viewer who pauses it is not overruled. Under reduced motion or
 * Save-Data it never starts by itself: the poster stands, the controls play it. The sound is the viewer's to turn on.
 */
export function FilmPlayer({ webm, mp4, poster, label }: Readonly<Props>) {
  const video = React.useRef<HTMLVideoElement>(null);
  const onScreen = useOnScreen(video, 0.5);
  const still = useStillMotion();
  const heldByViewer = React.useRef(false);
  const leaving = React.useRef(false);

  React.useEffect(() => {
    const el = video.current;
    if (!el || still) {
      return;
    }
    if (onScreen && !heldByViewer.current) {
      void el.play().catch(() => {});
    }
    if (!onScreen && !el.paused) {
      leaving.current = true;
      el.pause();
    }
  }, [onScreen, still]);

  return (
    <video
      ref={video}
      className="block aspect-video h-auto w-full rounded-lg border border-border-subtle bg-surface-2"
      width={1280}
      height={720}
      poster={poster}
      muted
      loop
      playsInline
      controls
      preload={still ? "none" : "metadata"}
      aria-label={label}
      data-testid="film-player"
      onPause={() => {
        // A pause we did not cause (the viewer's) holds the film until they press play again.
        heldByViewer.current = !leaving.current;
        leaving.current = false;
      }}
      onPlay={() => {
        heldByViewer.current = false;
      }}
    >
      <VideoSources webm={webm} mp4={mp4} />
    </video>
  );
}
