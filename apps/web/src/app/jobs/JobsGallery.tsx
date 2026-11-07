"use client";

import { formatDistanceToNow } from "date-fns";
import { enUS, fr } from "date-fns/locale";
import { ForwardIcon, ImageIcon, SoundIcon, VideoIcon } from "@krizaka/icons";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { jobKind, jobLabel, jobMediaUrl, jobPrompt, jobResultAlt } from "@/core/jobs/jobMedia";
import { ResultThumb } from "@/components/media/ResultThumb";
import { useTranslation } from "@/core/context/LocaleContext";
import { AudioWaveform } from "@/components/media/AudioWaveform";
import type { Job } from "@/core/types/jobs.types";

interface JobsGalleryProps {
  jobs: readonly Job[];
  /** Opens the job's result. */
  onOpen: (job: Job) => void;
  /** How many results to show (the latest first). */
  limit?: number;
}

const GLYPH = { image: ImageIcon, video: VideoIcon, audio: SoundIcon } as const;

/**
 * The latest images, videos and voice-overs this person generated, as what they are — a picture, a clip, a sound —
 * rather than as rows of ids. Generated on their machine: the gallery is the proof, so it comes first on the page.
 * Nothing is shown when nothing has been generated yet.
 */
export function JobsGallery({ jobs, onOpen, limit = 6 }: Readonly<JobsGalleryProps>) {
  const { t, locale } = useTranslation();
  const media = jobs
    .filter((job) => jobMediaUrl(job) && jobKind(job) !== "text")
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, limit);

  if (media.length === 0) return null;

  return (
    <section aria-label={t.showcase.jobs.result} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {media.map((job, index) => {
        const kind = jobKind(job) as keyof typeof GLYPH;
        const url = jobMediaUrl(job) ?? "";
        const Glyph = GLYPH[kind];
        const prompt = jobPrompt(job);
        return (
          <Card.Root key={job.id} reveal={index}>
            <Card.Media aspect={kind === "audio" ? "auto" : "video"}>
              {kind === "image" && <ResultThumb kind="image" src={url} alt={jobResultAlt(t, job)} />}
              {kind === "video" && <ResultThumb kind="video" src={url} alt={jobResultAlt(t, job)} autoPlay loop />}
              {kind === "audio" && (
                <div className="flex flex-col gap-3 bg-linear-to-br from-accent/15 via-media to-accent-2/15 p-4">
                  <SoundIcon size={28} className="text-fg-accent" nodeColor="var(--kz-accent)" />
                  <AudioWaveform src={url} label={jobLabel(t, job)} />
                </div>
              )}
            </Card.Media>
            <Card.Body className="gap-2">
              <div className="flex items-center gap-2">
                <Badge tone="accent">
                  <Glyph size={12} />
                  {jobLabel(t, job)}
                </Badge>
                <span className="ml-auto text-[11px] text-fg-muted">
                  {formatDistanceToNow(new Date(job.updatedAt), {
                    addSuffix: true,
                    locale: locale === "fr" ? fr : enUS,
                  })}
                </span>
              </div>
              {prompt && <Card.Description className="text-[13px]">{prompt}</Card.Description>}
              <Card.Footer>
                <Button variant="ghost" size="sm" className="-ml-2" onClick={() => onOpen(job)}>
                  {t.showcase.jobs.result}
                  <ForwardIcon size={14} />
                </Button>
              </Card.Footer>
            </Card.Body>
          </Card.Root>
        );
      })}
    </section>
  );
}
