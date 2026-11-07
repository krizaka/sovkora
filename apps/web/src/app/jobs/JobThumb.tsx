"use client";

import { ChatIcon, ImageIcon, SoundIcon, VideoIcon } from "@krizaka/icons";
import { ResultThumb } from "@/components/media/ResultThumb";
import { jobKind, jobMediaUrl, jobResultAlt } from "@/core/jobs/jobMedia";
import { useTranslation } from "@/core/context/LocaleContext";
import type { Job } from "@/core/types/jobs.types";

/** The preview of a job in its row: the picture, the first frame of the clip, or the kind's glyph. A box of fixed size. */
export function JobThumb({ job }: Readonly<{ job: Job }>) {
  const { t } = useTranslation();
  const kind = jobKind(job);
  const url = jobMediaUrl(job);
  const Glyph = kind === "video" ? VideoIcon : kind === "audio" ? SoundIcon : kind === "text" ? ChatIcon : ImageIcon;
  return (
    <span className="relative inline-grid h-11 w-16 shrink-0 place-items-center overflow-hidden rounded-md border border-border-subtle bg-surface-2 text-fg-muted">
      {url && (kind === "image" || kind === "video") ? (
        <ResultThumb kind={kind} src={kind === "video" ? `${url}#t=0.1` : url} alt={jobResultAlt(t, job)} compact />
      ) : (
        <Glyph size={18} />
      )}
    </span>
  );
}
