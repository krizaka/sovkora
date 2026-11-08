"use client";

import { useTranslation } from "@/core/context/LocaleContext";
import type { PackStudio } from "@/features/docs/utils/packs";

const chip = "rounded-md border border-border-subtle bg-surface-2 px-2 py-0.5 font-mono text-xs text-fg";

/**
 * One Studio of a pack: its name and id, what it does in two lines (the rest folds under « More »), what it asks for
 * as labelled chips, its steps and the credits held while it runs. Never a table row — a card, 1-up on a phone.
 */
export function StudioCard({ studio }: Readonly<{ studio: PackStudio }>) {
  const { t } = useTranslation();
  const p = t.docs.packs;
  const lead = studio.summary ?? studio.description;
  const rest = studio.summary && studio.description ? studio.description : null;
  return (
    <li className="flex flex-col gap-3 rounded-2xl border border-border-default bg-surface-1/85 p-4 sm:p-5">
      <header className="space-y-1">
        <h4 className="font-display text-base font-bold leading-snug text-fg">{studio.label}</h4>
        <p className="font-mono text-xs text-fg-muted">{studio.key}</p>
      </header>
      {lead && <p className="line-clamp-2 text-sm leading-relaxed text-fg-secondary">{lead}</p>}
      {rest && (
        <details className="group text-sm text-fg-secondary">
          <summary className="cursor-pointer select-none text-fg-accent marker:content-none">
            <span className="group-open:hidden">{p.more}</span>
            <span className="hidden group-open:inline">{p.less}</span>
          </summary>
          <p className="mt-2 leading-relaxed">{rest}</p>
        </details>
      )}
      <div className="space-y-1.5">
        <p className="font-mono text-[0.7rem] font-semibold text-fg-muted">{p.asks}</p>
        {studio.inputs.length ? (
          <ul className="flex flex-wrap gap-1.5">
            {studio.inputs.map((i) => (
              <li key={i.key} className={chip} title={i.key}>
                {i.title ?? i.key}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-xs text-fg-secondary">{p.noInputs}</p>
        )}
      </div>
      <dl className="mt-auto grid grid-cols-2 gap-3 border-t border-border-subtle pt-3 text-sm">
        <div>
          <dt className="font-mono text-[0.7rem] text-fg-muted">{p.steps}</dt>
          <dd className="text-fg">
            {studio.steps}{" "}
            <span className="text-xs text-fg-secondary">{studio.stepKinds.join(", ").toLowerCase()}</span>
          </dd>
        </div>
        <div>
          <dt className="font-mono text-[0.7rem] text-fg-muted">{p.credits}</dt>
          <dd className="text-fg">{studio.credits ?? "—"}</dd>
        </div>
      </dl>
    </li>
  );
}
