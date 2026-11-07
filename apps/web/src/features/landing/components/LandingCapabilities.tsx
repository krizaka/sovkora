"use client";

import { Section } from "@krizaka/ui/page";
import { useTranslation } from "@/core/context/LocaleContext";
import type { LandingDictionary } from "@/core/context/translations.landing.types";

/** The capabilities, in the order a buyer asks about them. */
const ORDER = [
  "documents",
  "models",
  "tools",
  "agents",
  "studios",
  "runs",
] as const satisfies readonly (keyof LandingDictionary["capabilities"]["items"])[];

/** Not shipped end to end yet: the home says « In development » beside it (RAG: the search engine exists, sending files from the app does not). */
const IN_DEVELOPMENT: ReadonlySet<(typeof ORDER)[number]> = new Set(["documents"]);

/**
 * What Sovkora does, in plain words: one H2, six short items, each with the industry term as a quiet tag (RAG, LLM, MCP…).
 * An item that is not shipped says « In development » — the claims are the ones the platform runs today.
 */
export function LandingCapabilities() {
  const { t } = useTranslation();
  const c = t.landing.capabilities;
  return (
    <Section id="capabilities" eyebrow={c.eyebrow} title={c.title} lead={c.lead} reveal divider>
      <ul className="grid gap-x-8 gap-y-8 md:grid-cols-2 lg:grid-cols-3" data-testid="landing-capabilities">
        {ORDER.map((id) => {
          const item = c.items[id];
          return (
            <li key={id} data-reveal className="flex min-w-0 flex-col gap-2">
              <p className="flex flex-wrap items-center gap-2 font-mono text-xs text-fg-secondary">
                <span className="rounded-md border border-border-subtle px-2 py-0.5">{item.tag}</span>
                {IN_DEVELOPMENT.has(id) && <span className="text-fg-accent">{c.soon}</span>}
              </p>
              <h3 className="font-display text-lg font-bold text-fg text-balance">{item.title}</h3>
              <p className="m-0 text-sm leading-relaxed text-pretty text-fg-secondary">{item.text}</p>
            </li>
          );
        })}
      </ul>
    </Section>
  );
}
