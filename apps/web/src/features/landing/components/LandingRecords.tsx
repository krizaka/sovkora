"use client";

import Image from "next/image";
import Link from "next/link";
import { ForwardIcon } from "@krizaka/icons";
import { Section } from "@krizaka/ui/page";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import { imagesOf } from "@/features/landing/utils/model-compare";
import type { MeasuredRuns } from "@/features/landing/utils/measured-runs";

/** The prompt shown: one of the published comparison's, with an image from each of the three models. */
const INSPIRATION = "montreal-winter";

/**
 * The proof, as three run records: the same prompt on three models, each image with where it ran, how long it took, what
 * it cost and its run id — read from the comparison's records (`model-compare.ts`), the figures under them from the
 * published measured runs. Nothing here is typed by hand, and the page that holds all of it is one link away.
 */
export function LandingRecords({ measured }: Readonly<{ measured: MeasuredRuns }>) {
  const { t } = useTranslation();
  const r = t.landing.records;
  const images = imagesOf(INSPIRATION);
  const title = r.prompt;
  return (
    <Section id="records" eyebrow={r.eyebrow} title={r.title} lead={r.lead} reveal divider>
      <span id="demo" className="block scroll-mt-20" />
      <ul className="grid gap-6 md:grid-cols-3">
        {images.map((image) => (
          <li key={image.stem} data-reveal className="flex min-w-0 flex-col gap-3">
            <Image
              src={image.src}
              alt={fill(r.alt, { model: image.model.name, title })}
              width={image.width}
              height={image.height}
              sizes="(max-width: 768px) 100vw, 360px"
              loading="lazy"
              unoptimized
              className="block h-auto w-full rounded-lg border border-border-subtle bg-surface-2"
            />
            <h3 className="font-display text-base font-bold text-fg">{image.model.name}</h3>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              <dt className="text-fg-secondary">{r.where}</dt>
              <dd className="m-0 text-fg">{image.model.where === "mac" ? r.onMac : r.onHosted}</dd>
              <dt className="text-fg-secondary">{r.time}</dt>
              <dd className="m-0 text-fg">{fill(r.timeValue, { seconds: image.seconds })}</dd>
              <dt className="text-fg-secondary">{r.cost}</dt>
              <dd className="m-0 text-fg">
                {image.model.costUsd === undefined
                  ? r.noCost
                  : fill(r.imageCost, { cost: image.model.costUsd.toFixed(2) })}
              </dd>
              <dt className="text-fg-secondary">{r.runLabel}</dt>
              <dd className="m-0 font-mono text-xs text-fg" title={image.runId}>
                {image.runId.slice(0, 8)}
              </dd>
            </dl>
          </li>
        ))}
      </ul>
      <p className="mt-8 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-fg-secondary">
        {measured.day && (
          <span>{fill(r.measuredOn, { day: measured.day, ok: measured.ok, models: measured.models })}</span>
        )}
        <Link
          href="/docs/measured-runs"
          className="inline-flex items-center gap-1 font-medium text-fg-accent hover:underline"
        >
          {r.more}
          <ForwardIcon size={16} />
        </Link>
      </p>
    </Section>
  );
}
