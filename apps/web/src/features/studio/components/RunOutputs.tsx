"use client";

import { useEffect, useRef, useState } from "react";
import { CheckIcon, CopyIcon, DownloadIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import type { RunArtefact } from "@krizaka/orazaka-shared";
import { useTranslation } from "@/core/context/LocaleContext";
import { asReport, ReportFindings } from "./ReportFindings";
import { artefactLabel } from "@/features/studio/utils/artefactLabel";
import { AudioWaveform } from "@/components/media/AudioWaveform";

interface RunOutputsProps {
  outputs: readonly RunArtefact[];
}

/**
 * What the run produced, rendered as the blueprint said it should be.
 *
 * Every value gets a copy button, because the whole point of the product is that the
 * professional takes the result somewhere else — a caption they cannot copy is a
 * caption they have to retype. Media gets a player or a preview as well: a Reel shown
 * as its asset id is a Reel the actor cannot tell succeeded.
 */
export function RunOutputs({ outputs }: Readonly<RunOutputsProps>) {
  const { t } = useTranslation();

  if (outputs.length === 0) {
    return null;
  }

  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-xs font-semibold text-fg-muted">{t.studio.runOutputs}</h2>
      <div className="grid gap-3 md:grid-cols-2">
        {outputs.map((artefact, index) => (
          <RunOutputRow key={artefact.key} artefact={artefact} index={index} wide={outputs.length === 1} />
        ))}
      </div>
    </section>
  );
}

interface RunOutputRowProps {
  artefact: RunArtefact;
  index?: number;
  /** The only result: it takes the whole width. */
  wide?: boolean;
}

/** One artefact: the media first (what impresses), then its label and its actions. */
function RunOutputRow({ artefact, index, wide }: Readonly<RunOutputRowProps>) {
  const { t, locale } = useTranslation();
  const [copied, setCopied] = useState(false);
  // The run row outlives its file (storage can be reset by a deploy): a media that 404s is said so.
  const [gone, setGone] = useState(false);
  const media =
    (artefact.type === "IMAGE" || artefact.type === "VIDEO" || artefact.type === "AUDIO") && artefact.value !== "";
  const report = artefact.type === "TEXT" ? asReport(artefact.value) : null;

  const copy = async () => {
    await navigator.clipboard.writeText(artefact.value);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <Card.Root reveal={index} className={media && !wide ? undefined : "md:col-span-2"}>
      {report && (
        <Card.Body padding="lg" className="gap-3 pb-0">
          <ReportFindings report={report} />
        </Card.Body>
      )}
      <ArtefactMedia artefact={artefact} gone={gone} onGone={() => setGone(true)} />
      <Card.Body className="gap-2">
        <header className="flex items-center gap-3">
          <span className="text-xs font-semibold text-fg-muted">{artefactLabel(artefact, locale)}</span>
          <span className="ml-auto flex items-center gap-1.5">
            {media && !gone && (
              <Button asChild variant="secondary" size="sm">
                <a href={artefact.value} download>
                  <DownloadIcon size={14} />
                  {t.showcase.studio.download}
                </a>
              </Button>
            )}
            <Button variant="secondary" size="sm" onClick={() => void copy()}>
              {copied ? <CheckIcon size={14} /> : <CopyIcon size={14} />}
              {copied ? t.studio.copied : t.studio.copy}
            </Button>
          </span>
        </header>
        {artefact.type === "TEXT" && !report ? (
          <p className="whitespace-pre-wrap break-words text-[13px] leading-relaxed text-fg">{artefact.value}</p>
        ) : null}
      </Card.Body>
    </Card.Root>
  );
}

/**
 * The playable or viewable form of a media artefact.
 *
 * The value is the public URL the executor published, so it is used as-is; a root-relative
 * path is resolved against the current origin the same way the jobs dashboard does it.
 */
function ArtefactMedia({
  artefact,
  gone,
  onGone,
}: Readonly<RunOutputRowProps & { gone: boolean; onGone: () => void }>) {
  const { t, locale } = useTranslation();
  const image = useRef<HTMLImageElement>(null);

  // An error that fired before hydration never reaches onError: look at the element once mounted.
  useEffect(() => {
    const element = image.current;
    if (element?.complete && element.naturalWidth === 0) {
      onGone();
    }
  }, [onGone]);

  if (gone) {
    return (
      <div role="status" className="flex flex-col gap-1 bg-media px-6 py-10 text-center">
        <p className="text-sm font-semibold text-fg">{t.studio.resultGone}</p>
        <p className="mx-auto max-w-prose text-[13px] leading-relaxed text-fg-muted">{t.studio.resultGoneBody}</p>
      </div>
    );
  }

  if (artefact.type === "TEXT" || artefact.value === "") {
    return null;
  }

  const source =
    globalThis.window !== undefined && artefact.value.startsWith("/")
      ? `${globalThis.location.origin}${artefact.value}`
      : artefact.value;

  if (artefact.type === "VIDEO") {
    return (
      <video src={source} controls playsInline onError={onGone} className="aspect-video w-full bg-media object-contain">
        <track kind="captions" />
      </video>
    );
  }

  if (artefact.type === "AUDIO") {
    return <AudioWaveform src={source} label={artefactLabel(artefact, locale)} className="px-4 pt-4" />;
  }

  return (
    /* eslint-disable-next-line @next/next/no-img-element -- a run artefact is a runtime URL,
       so there is no build-time source for next/image to optimise. */
    <img
      ref={image}
      src={source}
      onError={onGone}
      alt={artefactLabel(artefact, locale)}
      className="max-h-[520px] w-full bg-media object-contain"
    />
  );
}
