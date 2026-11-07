"use client";

import { useMemo, useState } from "react";
import { useTranslation } from "@/core/context/LocaleContext";
import { StudioCard } from "@/features/docs/components/StudioCard";
import { filterPacks, packAnchor, type PackData, type PackIndexItem } from "@/features/docs/utils/packs";

const chip = "rounded-sm border border-border-subtle bg-surface-2 px-2.5 py-0.5 font-mono text-xs text-fg";
const label = "font-mono text-[0.7rem] font-semibold text-fg-muted";

function Chips({
  name,
  values,
  current,
  onPick,
  all,
}: Readonly<{
  name: string;
  values: string[];
  current: string | null;
  onPick: (v: string | null) => void;
  all: string;
}>) {
  return (
    <div role="group" aria-label={name} className="flex flex-wrap items-center gap-1.5">
      <span className={label}>{name}</span>
      {[null, ...values].map((v) => (
        <button
          key={v ?? "all"}
          type="button"
          aria-pressed={current === v}
          onClick={() => onPick(v)}
          className="rounded-sm border border-border-subtle px-2.5 py-0.5 text-xs text-fg-secondary transition-colors aria-pressed:border-accent aria-pressed:bg-accent/10 aria-pressed:text-fg"
        >
          {v ?? all}
        </button>
      ))}
    </div>
  );
}

/** « Find a pack »: the packs as small cards, filtered by tier and category; each leads to its section below. */
export function PackIndexView({ items }: Readonly<{ items: PackIndexItem[] }>) {
  const { t } = useTranslation();
  const p = t.docs.packs;
  const [tier, setTier] = useState<string | null>(null);
  const [category, setCategory] = useState<string | null>(null);
  const shown = useMemo(() => filterPacks(items, tier, category), [items, tier, category]);
  const tiers = [...new Set(items.map((i) => i.tier).filter((x): x is string => Boolean(x)))];
  const categories = [...new Set(items.map((i) => i.category).filter((x): x is string => Boolean(x)))];
  return (
    <section aria-label={p.findLabel} className="not-prose my-6 space-y-4">
      <div className="space-y-2">
        <Chips name={p.tier} values={tiers} current={tier} onPick={setTier} all={p.all} />
        {categories.length > 1 && (
          <Chips name={p.category} values={categories} current={category} onPick={setCategory} all={p.all} />
        )}
        <p role="status" aria-live="polite" className="text-xs text-fg-secondary">
          {p.found.replace("{count}", String(shown.length)).replace("{total}", String(items.length))}
        </p>
      </div>
      {shown.length === 0 ? (
        <p className="text-sm text-fg-secondary">{p.none}</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {shown.map((i) => (
            <li key={i.key}>
              <a
                href={`#${packAnchor(i.title)}`}
                className="flex h-full flex-col gap-2 rounded-2xl border border-border-default bg-surface-1/85 p-4 transition-colors hover:border-accent focus-visible:outline-2 focus-visible:outline-accent"
              >
                <span className="font-display text-base font-bold text-fg">{i.title}</span>
                {i.tagline && <span className="line-clamp-2 text-sm text-fg-secondary">{i.tagline}</span>}
                <span className="mt-auto font-mono text-xs text-fg-muted">
                  {[
                    i.tier,
                    i.studios === 1 ? p.studiosOne : p.studios.replace("{count}", String(i.studios)),
                    i.category,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
              </a>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** A pack: its purpose, version and facts as chips, the controls as one calm note, its rulesets as a list, its Studios as cards. */
export function PackView({ pack }: Readonly<{ pack: PackData }>) {
  const { t } = useTranslation();
  const p = t.docs.packs;
  const notes = [
    pack.controls.scopeGuard && p.guard,
    pack.controls.consent && p.consent,
    pack.controls.safety && p.safety,
  ].filter(Boolean) as string[];
  return (
    <div className="not-prose my-6 space-y-6" data-pack={pack.key}>
      <div className="space-y-3">
        {pack.tagline && <p className="font-display text-lg font-semibold text-fg">{pack.tagline}</p>}
        {pack.description && <p className="max-w-3xl text-sm leading-relaxed text-fg-secondary">{pack.description}</p>}
        <ul aria-label={p.facts} className="flex flex-wrap items-center gap-1.5">
          <li className={chip}>
            <span className="font-semibold">{pack.key}</span> {pack.version}
          </li>
          {pack.tier && (
            <li className={chip}>
              {p.tierFact} {pack.tier}
            </li>
          )}
          {pack.kind && <li className={chip}>{pack.kind}</li>}
          {pack.distribution && <li className={chip}>{pack.distribution}</li>}
          {pack.regulatoryClass && (
            <li className={chip}>
              {p.regulatoryFact} {pack.regulatoryClass}
            </li>
          )}
          {pack.workers.map((w) => (
            <li key={w} className={chip}>
              {p.workerFact} {w}
            </li>
          ))}
        </ul>
        {notes.length > 0 && (
          <div
            role="note"
            className="max-w-3xl rounded-xl border border-border-subtle bg-surface-2/60 px-4 py-3 text-sm leading-relaxed text-fg-secondary"
          >
            {notes.map((n) => (
              <p key={n}>{n}</p>
            ))}
          </div>
        )}
      </div>

      {pack.rulesets.length > 0 && (
        <section aria-label={p.rulesets} className="space-y-2">
          <h4 className={label}>{p.rulesets}</h4>
          <ul className="divide-y divide-border-subtle rounded-xl border border-border-default bg-surface-1/60">
            {pack.rulesets.map((r) => (
              <li key={r.label} className="grid gap-x-4 gap-y-1 px-4 py-3 text-sm sm:grid-cols-[1fr_auto_auto]">
                <span className="font-medium text-fg">{r.label}</span>
                <span className="text-fg-secondary">
                  {r.jurisdiction ?? "—"} · {r.rules} {p.rules.toLowerCase()}
                </span>
                <span className="font-mono text-xs text-fg-muted">{r.versions.join(" · ")}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <ul className="grid gap-4 md:grid-cols-2">
        {pack.studios.map((s) => (
          <StudioCard key={s.key} studio={s} />
        ))}
      </ul>
    </div>
  );
}
