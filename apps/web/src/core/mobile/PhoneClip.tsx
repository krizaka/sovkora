"use client";

import type { ClipId } from "@/core/mobile/mobile";
import { APPEARANCES, clipUrls } from "@/core/mobile/mobile";
import { VideoSources } from "@/core/clips/VideoSources";

/**
 * One recording of the real app in a phone: the clip already carries its device frame (9:16), played in place on demand. The app was
 * recorded in dark and in light; the one that matches the page is shown (`html.light` hides the dark one, the dark page hides the light one: `globals.css`, « Phone recordings »). Nothing loads before the visitor presses play
 * (`preload="none"`, the poster stands in) and nothing autoplays — which is also what reduced motion asks: the poster
 * alone until play is pressed. `alt` is the text alternative of the recording; the caption under it is optional (a step
 * explains itself beside the phone).
 */
export function PhoneClip({
  id,
  label,
  alt,
  caption,
}: Readonly<{ id: ClipId; label: string; alt: string; caption?: string }>) {
  return (
    <figure className="flex w-full min-w-0 flex-col gap-3">
      <div className="mx-auto w-full max-w-[15rem] overflow-hidden rounded-2xl">
        {APPEARANCES.map((appearance) => {
          const u = clipUrls(id, appearance);
          return (
            <video
              key={appearance}
              controls
              playsInline
              preload="none"
              poster={u.poster}
              aria-label={`${label}. ${alt}`}
              data-appearance={appearance}
              className="block aspect-[1080/1920] w-full bg-surface-2 object-cover"
            >
              <VideoSources webm={u.webm} mp4={u.mp4} />
            </video>
          );
        })}
      </div>
      {caption && (
        <figcaption className="space-y-1 text-center">
          <span className="block font-mono text-xs font-semibold text-fg-accent">{label}</span>
          <span className="block text-sm leading-relaxed text-fg-secondary">{caption}</span>
        </figcaption>
      )}
    </figure>
  );
}
