"use client";

import { useState } from "react";
import { ImageIcon, VideoIcon } from "@krizaka/icons";
import { EmptyState } from "@krizaka/ui/empty-state";
import { useTranslation } from "@/core/context/LocaleContext";

interface ResultThumbProps {
  kind: "image" | "video";
  src: string;
  /** A short label ("Result of Image Generation"), never the prompt. */
  alt: string;
  /** A row thumbnail: the placeholder is a small icon, the words stay for assistive technology. */
  compact?: boolean;
  autoPlay?: boolean;
  loop?: boolean;
  className?: string;
}

/**
 * A result's picture or clip. When the file no longer loads (dev media are lost on redeploy), the box keeps its size and
 * shows a neutral « File expired » placeholder instead of a broken image with its alt text spilling over the card.
 */
export function ResultThumb({
  kind,
  src,
  alt,
  compact = false,
  autoPlay,
  loop,
  className,
}: Readonly<ResultThumbProps>) {
  const { t } = useTranslation();
  // The failure is kept against its own source, so a new source is tried again without an effect.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const Glyph = kind === "video" ? VideoIcon : ImageIcon;
  const expired = t.showcase.jobs.expired;
  const media = "h-full w-full object-cover";

  if (failedSrc === src) {
    if (compact) {
      return (
        <span role="img" aria-label={expired} title={expired} className="grid h-full w-full place-items-center">
          <Glyph size={18} />
        </span>
      );
    }
    return (
      <EmptyState
        className={className}
        icon={<Glyph size={24} />}
        title={expired}
        description={t.showcase.jobs.expiredBody}
      />
    );
  }

  if (kind === "video") {
    return (
      <video
        src={src}
        aria-label={alt}
        muted
        playsInline
        loop={loop}
        autoPlay={autoPlay}
        preload="metadata"
        onError={() => setFailedSrc(src)}
        className={className ?? media}
      >
        <track kind="captions" />
      </video>
    );
  }

  return (
    /* eslint-disable-next-line @next/next/no-img-element -- a generated asset is a runtime URL. */
    <img src={src} alt={alt} loading="lazy" onError={() => setFailedSrc(src)} className={className ?? media} />
  );
}
