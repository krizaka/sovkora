"use client";

import Link from "next/link";
import { DocsHubPage } from "@krizaka/ui/docs-hub-page";
import { CodeBlock } from "@krizaka/ui/code-block";
import { OrazakaLogo } from "@krizaka/ui";
import { LinkCard } from "@krizaka/ui/link-card";
import { PackIcon } from "@krizaka/icons";
import { useTranslation } from "@/core/context/LocaleContext";
import { DocsSearch } from "@/features/docs/components/DocsSearch";
import { PACKS_SLUG, pageHref, type JourneyId, type SearchEntry } from "@/features/docs/utils/docs-shared";

export interface HubJourney {
  id: JourneyId;
  href: string;
  links: { href: string; label: string }[];
}

/** `/docs` — the hub on @krizaka/ui's template: search first, then the five journeys, the quick start, the most read pages. */
export function DocsHub({
  index,
  journeys,
  quickstart,
  popular,
}: Readonly<{
  index: SearchEntry[];
  journeys: HubJourney[];
  quickstart: string;
  popular: { href: string; title: string; journey: JourneyId }[];
}>) {
  const { t } = useTranslation();
  const d = t.docs;
  return (
    <DocsHubPage
      linkAs={Link}
      eyebrow={d.eyebrow}
      title={d.title}
      lead={d.lead}
      search={<DocsSearch index={index} />}
      collectionsTitle={d.journeysTitle}
      collections={journeys.map((j) => ({
        id: j.id,
        brand: "orazaka",
        mark: <OrazakaLogo size={28} />,
        title: d.journeys[j.id].title,
        href: j.href,
        description: d.journeys[j.id].blurb,
        links: j.links,
      }))}
      quickstarts={{
        title: d.quickstartTitle,
        children: (
          <>
            <CodeBlock
              code={quickstart}
              language="bash"
              file={d.diagrams.terminal}
              copyLabel={d.copy}
              copiedLabel={d.copied}
            />
            <LinkCard
              linkAs={Link}
              href={pageHref(PACKS_SLUG)}
              icon={<PackIcon size={24} />}
              title={d.packsEntry.title}
              description={d.packsEntry.line}
              cta={d.packsEntry.cta}
            />
          </>
        ),
      }}
      popular={{
        title: d.popularTitle,
        items: popular.map((p) => ({ href: p.href, title: p.title, meta: d.journeys[p.journey].title })),
      }}
      promise={{ label: d.promiseLabel, text: d.promise }}
    />
  );
}
