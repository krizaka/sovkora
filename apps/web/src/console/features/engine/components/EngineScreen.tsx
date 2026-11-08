"use client";

import * as React from "react";
import Link from "next/link";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { featureToolsCopy } from "@/console/core/copy/featureTools";
import { engineCopy, type EngineLocale } from "@/console/core/copy/engine";
import { useEngine } from "@/console/features/engine/hooks/useEngine";
import { groupSettings } from "@/console/features/engine/utils/engine";
import { SettingCard } from "./SettingCard";

/** The page language (`<html lang>`), French or English. */
const pageLocale = (): EngineLocale =>
  typeof document !== "undefined" && document.documentElement.lang.toLowerCase().startsWith("fr") ? "fr" : "en";

/** The engine's runtime settings by group, edited in place by the administrator; a change applies on the next read. */
export function EngineScreen({ locale }: Readonly<{ locale?: EngineLocale }>) {
  const words = engineCopy[locale ?? pageLocale()];
  const engine = useEngine();
  const groups = React.useMemo(() => groupSettings(engine.settings), [engine.settings]);

  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{words.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{words.lead}</p>
        <Link href="/console/engine/tools" className="text-sm text-fg underline underline-offset-4">
          {featureToolsCopy[locale ?? pageLocale()].link}
        </Link>
      </header>
      {engine.hasError && !engine.isLoading && (
        <Alert
          tone="warning"
          action={
            <Button variant="secondary" size="sm" onClick={engine.reload}>
              {words.retry}
            </Button>
          }
        >
          {words.error}
        </Alert>
      )}
      {engine.isLoading && <output className="text-sm text-fg-secondary">{words.loading}</output>}
      {groups.map(({ group, settings }) => (
        <section key={group} aria-labelledby={`engine-${group}`} className="flex flex-col gap-3">
          <h2 id={`engine-${group}`} className="text-lg font-semibold text-fg">
            {words.groups[group]}
          </h2>
          <div className="grid gap-4 lg:grid-cols-2">
            {settings.map((setting) => (
              <SettingCard key={setting.key} setting={setting} words={words} onSave={engine.save} />
            ))}
          </div>
        </section>
      ))}
    </article>
  );
}
