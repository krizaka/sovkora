"use client";

import { Section } from "@krizaka/ui/page";
import { useTranslation } from "@/core/context/LocaleContext";
import { mediaUrl } from "@/core/media/cdn";
import { FilmPlayer } from "@/features/landing/components/FilmPlayer";
import type { FilmFacts } from "@/features/landing/utils/film-records";

const FILM = "sites/orazaka/films/montrer-son-travail";

/**
 * « Made with Sovkora »: the brand film, which Sovkora itself made, and the run facts under it. The facts are read from the
 * film's records (`film-records.ts`) and each one appears only when the records hold it; the run ids stay one click away
 * so the claim can be checked. The film's own title is the film's, not the product's, and stays in French.
 */
export function LandingFilm({ facts }: Readonly<{ facts: FilmFacts }>) {
  const { t, locale } = useTranslation();
  const f = t.landing.film;
  const money = new Intl.NumberFormat(locale, { style: "currency", currency: "USD" });
  const rows: { label: string; value: string }[] = [];
  if (facts.models.length) {
    rows.push({ label: f.models, value: facts.models.map((m) => `${m.name} (${f.roles[m.role]})`).join(" · ") });
  }
  if (facts.gpus.length) {
    rows.push({ label: f.gpu, value: facts.gpus.join(" · ") });
  }
  if (facts.runs) {
    rows.push({ label: f.runs, value: new Intl.NumberFormat(locale).format(facts.runs) });
  }
  if (facts.gpuCostUsd !== null) {
    rows.push({ label: f.cost, value: money.format(facts.gpuCostUsd) });
  }
  return (
    <Section id="made-with-orazaka" eyebrow={f.eyebrow} title={f.title} lead={f.lead} reveal divider>
      <div className="grid gap-8 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start" data-testid="landing-film">
        <FilmPlayer
          webm={mediaUrl(`${FILM}.webm`)}
          mp4={mediaUrl(`${FILM}.mp4`)}
          poster={mediaUrl(`${FILM}-poster.webp`)}
          label={f.label}
        />
        {rows.length > 0 && (
          <div className="flex min-w-0 flex-col gap-3">
            <h3 className="font-display text-base font-bold text-fg">{f.factsTitle}</h3>
            <dl className="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm" data-testid="film-facts">
              {rows.map((row) => (
                <div key={row.label} className="contents">
                  <dt className="text-fg-secondary">{row.label}</dt>
                  <dd className="m-0 min-w-0 text-fg">{row.value}</dd>
                </div>
              ))}
            </dl>
            {facts.gpuCostUsd !== null && <p className="m-0 text-xs text-fg-secondary text-pretty">{f.costNote}</p>}
            {facts.runIds.length > 0 && (
              <details className="text-sm">
                <summary className="cursor-pointer font-medium text-fg-accent">{f.runIds}</summary>
                <ol className="mt-2 flex list-none flex-col gap-1 p-0 font-mono text-xs text-fg-secondary">
                  {facts.runIds.map((id) => (
                    <li key={id} className="break-all">
                      {id}
                    </li>
                  ))}
                </ol>
              </details>
            )}
          </div>
        )}
      </div>
    </Section>
  );
}
