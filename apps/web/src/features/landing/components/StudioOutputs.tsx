"use client";

import * as React from "react";
import { cn } from "@krizaka/ui/cn";
import { VideoSources } from "@/core/clips/VideoSources";
import { useOnScreen, useStillMotion } from "@/core/clips/usePlayback";
import { mediaUrl } from "@/core/media/cdn";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import type { FilmFacts } from "@/features/landing/utils/film-records";

const FILMS = "sites/orazaka/films";
/** How long each real output stays before the next one fades in. */
// @fixed: a reading pace for a still or a three-second shot, not a business parameter.
const HOLD_MS = 4500;

/** The outputs in turn: the key frame the image Studio made, then the shot the video Studio made from it. */
const SLIDES = [
  { role: "keyframes", caption: "imageCaption", file: "keyframe-5", alt: "duckKeyframe" },
  { role: "shots", caption: "videoCaption", file: "shot-duck", shot: "s5-01-a14br", alt: "duckShot" },
  { role: "keyframes", caption: "imageCaption", file: "keyframe-2", alt: "inkKeyframe" },
  { role: "shots", caption: "videoCaption", file: "shot-ink-drop", shot: "s2-01-a14b", alt: "inkShot" },
] as const;

/**
 * Real outputs on the Studios card: two key frames from the image Studio and the two shots the video Studio made from
 * them, cross-fading while the card is on screen. The caption names the Studio, the model and, for a shot, its run id
 * (read from the film's records). Under reduced motion or Save-Data the first key frame stands still. The box is
 * 16:9 from the first paint, so nothing shifts.
 */
export function StudioOutputs({ facts }: Readonly<{ facts: FilmFacts }>) {
  const { t } = useTranslation();
  const w = t.landing.film.outputs;
  const box = React.useRef<HTMLDivElement>(null);
  const onScreen = useOnScreen(box, 0.3);
  const still = useStillMotion();
  const [index, setIndex] = React.useState(0);
  const videos = React.useRef<(HTMLVideoElement | null)[]>([]);
  const moving = onScreen && !still;

  React.useEffect(() => {
    if (!moving) {
      return;
    }
    const timer = setInterval(() => setIndex((i) => (i + 1) % SLIDES.length), HOLD_MS);
    return () => clearInterval(timer);
  }, [moving]);

  React.useEffect(() => {
    videos.current.forEach((el, i) => {
      if (!el) {
        return;
      }
      if (moving && i === index) {
        el.currentTime = 0;
        void el.play().catch(() => {});
      } else {
        el.pause();
      }
    });
  }, [index, moving]);

  const slide = SLIDES[still ? 0 : index];
  const model = facts.models.find((m) => m.role === slide.role);
  const run = "shot" in slide ? facts.runIdOf[slide.shot] : undefined;
  return (
    <figure className="m-0" data-testid="studio-outputs">
      <div
        ref={box}
        className="relative aspect-video w-full overflow-hidden rounded-lg border border-border-subtle bg-surface-2"
      >
        {SLIDES.map((s, i) => {
          const active = (still ? 0 : index) === i;
          const layer = cn(
            "absolute inset-0 size-full object-cover transition-opacity duration-700 motion-reduce:transition-none",
            active ? "opacity-100" : "opacity-0",
          );
          return !("shot" in s) ? (
            // eslint-disable-next-line @next/next/no-img-element -- a CDN image in a box of fixed shape
            <img
              key={s.file}
              src={mediaUrl(`${FILMS}/${s.file}.webp`)}
              alt={active ? w[s.alt] : ""}
              width={1280}
              height={720}
              loading="lazy"
              decoding="async"
              className={layer}
            />
          ) : (
            <video
              key={s.file}
              ref={(el) => {
                videos.current[i] = el;
              }}
              className={layer}
              width={960}
              height={540}
              poster={mediaUrl(`${FILMS}/${s.file}-poster.webp`)}
              muted
              loop
              playsInline
              preload={onScreen && !still ? "metadata" : "none"}
              aria-label={active ? w[s.alt] : undefined}
              aria-hidden={active ? undefined : true}
            >
              <VideoSources webm={mediaUrl(`${FILMS}/${s.file}.webm`)} mp4={mediaUrl(`${FILMS}/${s.file}.mp4`)} />
            </video>
          );
        })}
      </div>
      <figcaption className="px-1 pt-2 font-mono text-xs text-fg-secondary" aria-live="off">
        {fill(w[slide.caption], {
          model: model?.name ?? "",
          run: run?.slice(0, 8) ?? "",
        })}
      </figcaption>
    </figure>
  );
}
