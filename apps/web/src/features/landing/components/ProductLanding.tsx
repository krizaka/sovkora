"use client";

import Link from "next/link";
import { ForwardIcon } from "@krizaka/icons";
import { DevelopmentStatus } from "@/components/DevelopmentStatus";
import { Button } from "@krizaka/ui/button";
import { LandingPage as LandingTemplate } from "@krizaka/ui/landing-page";
import { NumberTicker } from "@krizaka/ui/number-ticker";
import { publicBuild } from "@/core/config/build-target";
import { useTranslation } from "@/core/context/LocaleContext";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { SectionMedia } from "@/features/landing/components/SectionMedia";
import { LandingCapabilities } from "@/features/landing/components/LandingCapabilities";
import { LandingFilm } from "@/features/landing/components/LandingFilm";
import { StudioOutputs } from "@/features/landing/components/StudioOutputs";
import { NO_FILM_FACTS, type FilmFacts } from "@/features/landing/utils/film-records";
import { LandingRecords } from "@/features/landing/components/LandingRecords";
import { useEntries } from "@/features/landing/components/useEntries";
import type { LandingDictionary } from "@/core/context/translations.landing.types";
import type { MeasuredRuns } from "@/features/landing/utils/measured-runs";
import "@/features/landing/landing.css";

/** The three capabilities; each shows the media the registry gives its role (`content/media-registry.json`, page `home`). */
const FEATURES = [
  { id: "studios", size: "lg" },
  { id: "records", size: "sm" },
  { id: "home", size: "sm" },
] as const satisfies readonly { id: keyof LandingDictionary["features"]["items"]; size: "lg" | "sm" }[];

/** The closing call is the quickstart: the hero already carries the way in (sign-up or the docs), so the action is not said twice. */
const QUICKSTART_HREF = "/docs/101";

const NO_RUNS: MeasuredRuns = { ok: 0, all: 0, models: 0, day: null };

/**
 * The public home page, on the product landing template of @krizaka/ui (the one of Yomizaka's redesign): one statement, one
 * line, one action and a quiet second one, beside a recording of the app — the media the registry gives each role, never a mock-up. Then the
 * figures (counted from the published measured runs), three things it does on real screens, the proof as run records,
 * where it runs, the products that run on it, the honest comparison, the questions, the closing call. Every word comes
 * from `t.landing` (en/fr); the details live on their pages (/models, /gallery, /desktop, /docs).
 */
export function ProductLanding({
  measured = NO_RUNS,
  film = NO_FILM_FACTS,
  quickstartHref = QUICKSTART_HREF,
}: Readonly<{
  measured?: MeasuredRuns;
  film?: FilmFacts;
  /** The closing call's page; `null` when the quickstart is not published (the public export publishes a part of the docs). */
  quickstartHref?: string | null;
}>) {
  const { t, locale } = useTranslation();
  const h = t.landing.hero;
  const f = t.landing.features;
  const entries = useEntries();
  const primary = entries.primary(t.landing.cta.primary);

  return (
    <>
      <LandingNav />
      <main id="main" className="bg-surface-0 text-fg">
        <LandingTemplate
          className="landing-home"
          title={h.title}
          lead={h.lead}
          visualLabel={h.visualLabel}
          visual={<SectionMedia page="home" role="hero" priority />}
          actions={
            <>
              <Button asChild variant="primary" size="lg" className="w-full sm:w-auto">
                <Link href={primary.href}>
                  {primary.label}
                  <ForwardIcon size={16} strokeWidth={2.25} />
                </Link>
              </Button>
              <Button asChild variant="ghost" size="lg" className="w-full sm:w-auto">
                {/* The public export has no run records section: the quiet action leads to what it does. */}
                <Link href={publicBuild ? "#features" : "#records"}>{h.secondary}</Link>
              </Button>
              <DevelopmentStatus className="basis-full" />
            </>
          }
          proofs={[
            { value: <NumberTicker value={measured.ok} locale={locale} />, label: t.landing.figures.runs },
            { value: <NumberTicker value={measured.models} locale={locale} />, label: t.landing.figures.models },
            { value: <NumberTicker value={0} locale={locale} />, label: t.landing.figures.telemetry },
          ]}
          featuresHeading={{ eyebrow: f.eyebrow, title: f.title, lead: f.lead }}
          features={FEATURES.map(({ id, size }) => ({
            id,
            size,
            title: f.items[id].title,
            text: f.items[id].text,
            visual:
              id === "studios" ? (
                <div className="flex flex-col gap-4">
                  <SectionMedia page="home" role={id} />
                  <StudioOutputs facts={film} />
                </div>
              ) : (
                <SectionMedia page="home" role={id} />
              ),
          }))}
          closing={{
            title: t.landing.cta.title,
            body: t.landing.cta.body,
            // Without a published quickstart (the public export), the call is the documentation that is published.
            actions: (
              <Button asChild variant="primary" size="lg">
                {quickstartHref ? (
                  <Link href={quickstartHref}>{t.landing.cta.quickstart}</Link>
                ) : (
                  <Link href="/docs">{t.landing.closed.docs}</Link>
                )}
              </Button>
            ),
          }}
        >
          <LandingCapabilities />
          <LandingFilm facts={film} />
          <LandingRecords measured={measured} />
        </LandingTemplate>
      </main>
      <LandingFooter />
    </>
  );
}
