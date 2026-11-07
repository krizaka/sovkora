"use client";

import { useId } from "react";
import Link from "next/link";
import { cn } from "@krizaka/ui/cn";
import { useTranslation } from "@/core/context/LocaleContext";
import { FOLDED_JOURNEY, JOURNEY_IDS, pageHref, type NavItem } from "@/features/docs/utils/docs-shared";

/** The pages of the documentation by journey; the one being read is marked (`aria-current`). */
export function DocsSidebar({ items, current }: Readonly<{ items: readonly NavItem[]; current: string | null }>) {
  const { t } = useTranslation();
  const d = t.docs;
  // The template draws the sidebar twice (desktop column, mobile disclosure): the ids that name a section are per copy.
  const uid = useId();
  return (
    <nav aria-label={d.sidebarLabel} className="space-y-6 text-sm">
      <Link
        href="/docs"
        className={cn(
          "block font-mono text-xs font-semibold hover:text-fg",
          current === null ? "text-fg-accent" : "text-fg-secondary",
        )}
      >
        {d.home}
      </Link>
      {JOURNEY_IDS.map((j) => {
        const pages = items.filter((i) => i.journey === j);
        const groups = [...new Set(pages.map((p) => p.group))];
        const list = (
          <>
            {groups.map((g) => (
              <div key={g ?? "main"}>
                {g && <p className="mb-1 mt-3 px-2.5 font-mono text-[0.68rem] text-fg-muted">{d.groups[g]}</p>}
                <ul className="m-0 list-none space-y-0.5 p-0">
                  {pages
                    .filter((p) => p.group === g)
                    .map((p) => (
                      <li key={p.slug}>
                        <Link
                          href={pageHref(p.slug)}
                          aria-current={p.slug === current ? "page" : undefined}
                          className={cn(
                            "block rounded-lg border-l-2 border-transparent px-2.5 py-1.5 leading-snug text-fg-secondary transition-colors hover:bg-surface-2 hover:text-fg",
                            p.slug === current && "border-fg-accent bg-surface-2 font-medium text-fg",
                          )}
                        >
                          {p.title}
                        </Link>
                      </li>
                    ))}
                </ul>
              </div>
            ))}
          </>
        );
        const heading = "m-0 font-display text-sm font-semibold text-fg";
        // The reference is generated and long: it stays folded until the page being read belongs to it.
        return j === FOLDED_JOURNEY ? (
          <details key={j} open={pages.some((p) => p.slug === current)} className="space-y-1.5">
            <summary className={cn(heading, "cursor-pointer")}>{d.journeys[j].title}</summary>
            {list}
          </details>
        ) : (
          <section key={j} aria-labelledby={`${uid}-${j}`} className="space-y-1.5">
            <h2 id={`${uid}-${j}`} className={heading}>
              {d.journeys[j].title}
            </h2>
            {list}
          </section>
        );
      })}
    </nav>
  );
}
