"use client";

import Link from "next/link";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { featureToolsCopy, type FeatureToolsWords, type FeatureToolsLocale } from "@/console/core/copy/featureTools";
import { useFeatureTools } from "@/console/features/engine/hooks/useFeatureTools";
import { BoundsSection } from "./BoundsSection";
import { CachePolicySection } from "./CachePolicySection";

/** The page language (`<html lang>`), French or English. */
const pageLocale = (): FeatureToolsLocale =>
  typeof document !== "undefined" && document.documentElement.lang.toLowerCase().startsWith("fr") ? "fr" : "en";

/** The words of the structured-output section: the same fields as the tool-round bounds, said for answers. */
const structuredWords = (words: FeatureToolsWords): FeatureToolsWords => ({
  ...words,
  defaultRow: words.structuredDefaultRow,
  rounds: words.answers,
  range: words.answersRange,
  invalidRange: words.invalidAnswers,
  saved: words.savedAnswers,
  addTitle: words.structuredAddTitle,
  featureKey: words.structuredFeatureKey,
  add: words.addStructured,
});

/** The per-feature tool-round bounds, edited in place by the administrator, and the feature routes (read only). */
export function FeatureToolsScreen({ locale }: Readonly<{ locale?: FeatureToolsLocale }>) {
  const words = featureToolsCopy[locale ?? pageLocale()];
  const tools = useFeatureTools();
  const range = tools.bounds ? { min: tools.bounds.min, max: tools.bounds.max } : null;

  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <Link href="/console/engine" className="text-sm text-fg-secondary underline-offset-4 hover:underline">
          {words.back}
        </Link>
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{words.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{words.lead}</p>
      </header>
      {tools.hasError && !tools.isLoading && (
        <Alert
          tone="warning"
          action={
            <Button variant="secondary" size="sm" onClick={tools.reload}>
              {words.retry}
            </Button>
          }
        >
          {words.error}
        </Alert>
      )}
      {tools.isLoading && <output className="text-sm text-fg-secondary">{words.loading}</output>}
      {range && tools.bounds && (
        <BoundsSection
          kind="tools"
          title={words.boundsTitle}
          bounds={tools.bounds.bounds.map((b) => ({
            ...b,
            value: b.maxToolRounds,
          }))}
          range={range}
          words={words}
          onSave={tools.save}
          onRemove={tools.remove}
        />
      )}
      {tools.structured && (
        <BoundsSection
          kind="structured"
          title={words.structuredTitle}
          lead={words.structuredLead}
          bounds={tools.structured.bounds.map((b) => ({
            ...b,
            value: b.maxAttempts,
          }))}
          range={tools.structured}
          words={structuredWords(words)}
          onSave={tools.saveStructured}
          onRemove={tools.removeStructured}
        />
      )}
      {tools.cache && (
        <CachePolicySection cache={tools.cache} words={words} onSave={tools.saveCache} onRemove={tools.removeCache} />
      )}
      <section aria-labelledby="tools-routes" className="flex flex-col gap-3">
        <h2 id="tools-routes" className="text-lg font-semibold text-fg">
          {words.routesTitle}
        </h2>
        <p className="text-sm text-fg-secondary">{words.routesLead}</p>
        {tools.routesFailed && <Alert tone="warning">{words.routesError}</Alert>}
        {!tools.routesFailed && tools.routes.length === 0 && !tools.isLoading && (
          <p className="text-sm text-fg-muted">{words.routesEmpty}</p>
        )}
        <ul className="grid gap-3 lg:grid-cols-2">
          {tools.routes.map((route) => (
            <li key={route.featureKey} data-route={route.featureKey}>
              <Card.Root className="flex flex-col gap-2 p-4">
                <span className="font-mono text-sm font-semibold text-fg">{route.featureKey}</span>
                <ol className="flex flex-wrap gap-2">
                  {route.models.map((model, i) => (
                    <li key={model}>
                      <Badge tone={i === 0 ? "accent" : "neutral"}>{model}</Badge>
                    </li>
                  ))}
                </ol>
              </Card.Root>
            </li>
          ))}
        </ul>
      </section>
    </article>
  );
}
