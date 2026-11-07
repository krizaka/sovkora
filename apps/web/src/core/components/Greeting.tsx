"use client";

import * as React from "react";
import { useAuth } from "@/core/hooks/useAuth";
import { useTranslation } from "@/core/context/LocaleContext";

type Period = "morning" | "afternoon" | "evening";

/** The part of the day a greeting speaks to. */
export function dayPeriod(hour: number): Period {
  if (hour < 12) return "morning";
  if (hour < 18) return "afternoon";
  return "evening";
}

/**
 * Splits a greeting template around its `{name}` so the name can carry the accent:
 * `"Good evening, {name}"` → `["Good evening, ", "Eric", ""]`.
 */
export function greetingParts(template: string, name: string): [string, string, string] {
  const at = template.indexOf("{name}");
  if (at < 0) return [template, "", ""];
  return [template.slice(0, at), name, template.slice(at + "{name}".length)];
}

const noop = () => () => {};
const currentHour = () => new Date().getHours();
const serverHour = () => null;

/**
 * "Good evening, Eric" / "Bonsoir Eric" — the signed-in user's first name, in the accent, after the
 * greeting for the time of day. The words come from the dictionary (`dashboard.greeting`), never
 * from the component, so French reads as French and not as a translated English sentence.
 */
export function Greeting({ suffix }: Readonly<{ suffix?: string }>) {
  const { user } = useAuth();
  const { t } = useTranslation();
  // The hour is the viewer's: the server render (and hydration) say "welcome", the browser says when.
  const hour = React.useSyncExternalStore(noop, currentHour, serverHour);

  const name = user?.name?.split(" ")[0] ?? "";
  if (hour === null || !name) return <>{t.dashboard.welcome}</>;
  const [before, who, after] = greetingParts(t.dashboard.greeting[dayPeriod(hour)], name);
  return (
    <>
      {before}
      <span className="text-accent">{who}</span>
      {after}
      {suffix}
    </>
  );
}
