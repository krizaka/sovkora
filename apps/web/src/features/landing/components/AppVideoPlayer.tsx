"use client";

import { cn } from "@krizaka/ui/cn";
import { useState, useSyncExternalStore } from "react";
import { VideoSources } from "@/core/clips/VideoSources";

const REDUCE = "(prefers-reduced-motion: reduce)";
const subscribe = (notify: () => void) => {
  const query = window.matchMedia(REDUCE);
  query.addEventListener("change", notify);
  return () => query.removeEventListener("change", notify);
};
const useReducedMotion = () =>
  useSyncExternalStore(
    subscribe,
    () => window.matchMedia(REDUCE).matches,
    () => true,
  );

interface Props {
  poster: string;
  webm: string;
  mp4: string;
  /** Width and height of the recording: the box keeps its shape before and after the play (no layout shift). */
  width: number;
  height: number;
  play: string;
  label: string;
  /** WebVTT tracks (English and French); off until the viewer turns one on. */
  captions?: readonly { lang: string; label: string; src: string }[];
}

/** The poster first; the player is only created when asked (nothing is downloaded before), in a box of the same size. */
export function AppVideoPlayer({ poster, webm, mp4, width, height, play, label, captions = [] }: Readonly<Props>) {
  const [on, setOn] = useState(false);
  // Reduced motion: the poster is the state of the page; the film plays only when asked, once, with its controls (no loop).
  const still = useReducedMotion();
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-lg border border-border-default bg-surface-1",
        width >= height ? "aspect-video" : "aspect-[9/16]",
      )}
    >
      {on ? (
        <video
          className="absolute inset-0 size-full object-cover"
          controls
          autoPlay={!still}
          muted
          loop={!still}
          playsInline
          poster={poster}
          aria-label={label}
        >
          <VideoSources webm={webm} mp4={mp4} />
          {captions.map((c) => (
            <track key={c.lang} kind="captions" srcLang={c.lang} label={c.label} src={c.src} />
          ))}
        </video>
      ) : (
        <button
          type="button"
          onClick={() => setOn(true)}
          className="group absolute inset-0 size-full cursor-pointer"
          aria-label={`${play}: ${label}`}
        >
          {/* eslint-disable-next-line @next/next/no-img-element -- a CDN poster in a box of fixed shape */}
          <img
            src={poster}
            alt=""
            width={width}
            height={height}
            loading="lazy"
            decoding="async"
            className="size-full object-cover"
          />
          <span className="absolute inset-0 grid place-items-center">
            <span className="rounded-md bg-surface-1/90 px-4 py-2 text-sm font-medium text-fg shadow group-hover:bg-surface-1">
              {play}
            </span>
          </span>
        </button>
      )}
    </div>
  );
}
