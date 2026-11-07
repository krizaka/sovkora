"use client";

import Link from "next/link";
import { ForwardIcon } from "@krizaka/icons";
import { DevelopmentStatus } from "@/components/DevelopmentStatus";
import { Button } from "@krizaka/ui/button";
import { LandingPage as LandingTemplate } from "@krizaka/ui/landing-page";
import { NumberTicker } from "@krizaka/ui/number-ticker";
import { useTranslation } from "@/core/context/LocaleContext";
import { Clip } from "@/core/clips/Clip";
import type { ClipId } from "@/core/clips/clips";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { PhoneSection } from "@/features/landing/components/PhoneSection";
import { LandingCapabilities } from "@/features/landing/components/LandingCapabilities";
import { LandingFilm } from "@/features/landing/components/LandingFilm";
import { StudioOutputs } from "@/features/landing/components/StudioOutputs";
import { NO_FILM_FACTS, type FilmFacts } from "@/features/landing/utils/film-records";
import { LandingRecords } from "@/features/landing/components/LandingRecords";
import { useEntries } from "@/features/landing/components/useEntries";
import type { LandingDictionary } from "@/core/context/translations.landing.types";
import type { MeasuredRuns } from "@/features/landing/utils/measured-runs";
import "@/features/landing/landing.css";

/** The three capabilities, each shown by the clip of its screen. */
const FEATURES = [
  { id: "studios", size: "lg", clip: "studio" },
  { id: "records", size: "sm", clip: "results" },
  { id: "home", size: "sm", clip: "workspace" },
] as const satisfies readonly { id: keyof LandingDictionary["features"]["items"]; size: "lg" | "sm"; clip: ClipId }[];

/** The closing call is the quickstart: the hero already carries the way in (sign-up or the docs), so the action is not said twice. */
const QUICKSTART_HREF = "/docs/101";

const NO_RUNS: MeasuredRuns = { ok: 0, all: 0, models: 0, day: null };

/**
 * The public home page, on the product landing template of @krizaka/ui (the one of Yomizaka's redesign): one statement, one
 * line, one action and a quiet second one, beside the real chat answering — a recording of the app, not a mock-up. Then the
 * figures (counted from the published measured runs), three things it does on real screens, the proof as run records,
 * where it runs, the products that run on it, the honest comparison, the questions, the closing call. Every word comes
 * from `t.landing` (en/fr); the details live on their pages (/models, /gallery, /desktop, /docs).
 */
export function LandingPage({
  measured = NO_RUNS,
  film = NO_FILM_FACTS,
}: Readonly<{ measured?: MeasuredRuns; film?: FilmFacts }>) {
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
          visual={<Clip id="take" priority />}
          actions={
            <>
              <Button asChild variant="primary" size="lg" className="w-full sm:w-auto">
                <Link href={primary.href}>
                  {primary.label}
                  <ForwardIcon size={16} strokeWidth={2.25} />
                </Link>
              </Button>
              <Button asChild variant="ghost" size="lg" className="w-full sm:w-auto">
                <Link href="#records">{h.secondary}</Link>
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
          features={FEATURES.map(({ id, size, clip }) => ({
            id,
            size,
            title: f.items[id].title,
            text: f.items[id].text,
            visual:
              id === "studios" ? (
                <div className="flex flex-col gap-4">
                  <Clip id={clip} />
                  <StudioOutputs facts={film} />
                </div>
              ) : (
                <Clip id={clip} />
              ),
          }))}
          closing={{
            title: t.landing.cta.title,
            body: t.landing.cta.body,
            actions: (
              <>
                <Button asChild variant="primary" size="lg">
                  <Link href={QUICKSTART_HREF}>{t.landing.cta.quickstart}</Link>
                </Button>
              </>
            ),
          }}
        >
          <LandingCapabilities />
          <LandingFilm facts={film} />
          <LandingRecords measured={measured} />
          <PhoneSection id="phone-studio" words={t.landing.phone} testId="landing-phone" />
        </LandingTemplate>
      </main>
      <LandingFooter />
    </>
  );
}
