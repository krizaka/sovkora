"use client";

import { useTranslation } from "@/core/context/LocaleContext";
import { DocArticle } from "@/features/docs/components/DocArticle";
import { MobileStep } from "@/core/mobile/MobileStep";
import { STEPS } from "@/core/mobile/mobile";
import { editUrl, type DocEntry, type NavItem } from "@/features/docs/utils/docs-shared";

/** The four-step tour of the app inside the documentation: one article, each step a recording and the three things to notice. */
export function MobileTour({
  entry,
  nav,
  previous,
  next,
}: Readonly<{
  entry: DocEntry;
  nav: NavItem[];
  previous: { slug: string; title: string } | null;
  next: { slug: string; title: string } | null;
}>) {
  const { t } = useTranslation();
  const m = t.mobile;
  return (
    <DocArticle
      slug={entry.slug}
      journey="guides"
      title={entry.title}
      intro={entry.intro}
      audience={entry.audience}
      generated={false}
      editUrl={editUrl(entry)}
      toc={STEPS.map((id, i) => ({ id: `mobile-step-${id}`, label: `${i + 1}. ${m.steps[id].goal}` }))}
      nav={nav}
      previous={previous}
      next={next}
      words={{ title: m.title, lead: m.lead }}
    >
      <p className="font-mono text-xs text-fg-muted">{m.recordedOn}</p>
      <ol className="mt-10 space-y-16 p-0">
        {STEPS.map((id, i) => (
          <MobileStep key={id} id={id} index={i} reverse={false} />
        ))}
      </ol>
    </DocArticle>
  );
}
