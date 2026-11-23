"use client";

import Link from "next/link";
import { DocsHubPage } from "@krizaka/ui/docs-hub-page";
import { CodeBlock } from "@krizaka/ui/code-block";
import { SovkoraLogo } from "@krizaka/ui";
import { LinkCard } from "@krizaka/ui/link-card";
import { PackIcon } from "@krizaka/icons";
import { publicBuild } from "@/core/config/build-target";
import { useTranslation } from "@/core/context/LocaleContext";
import { sectionMedia } from "@/core/clips/media-registry";
import { LandingSectionHeading } from "@/core/components/LandingSectionHeading";
import { SectionMedia } from "@/core/components/SectionMedia";
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
  packs = true,
}: Readonly<{
  index: SearchEntry[];
  journeys: HubJourney[];
  quickstart: string;
  popular: { href: string; title: string; journey: JourneyId }[];
  /** The packs page is published: its entry block follows the quickstart (the public export publishes a part of the pages). */
  packs?: boolean;
}>) {
  const { t } = useTranslation();
  const d = t.docs;
  return (
    <>
      <DocsHubPage
        linkAs={Link}
        eyebrow={d.eyebrow}
        title={d.title}
        lead={publicBuild ? d.publicLead : d.lead}
        search={<DocsSearch index={index} />}
        collectionsTitle={d.journeysTitle}
        collections={journeys.map((j) => ({
          id: j.id,
          brand: "sovkora",
          mark: <SovkoraLogo size={28} />,
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
              {packs && (
                <LinkCard
                  linkAs={Link}
                  href={pageHref(PACKS_SLUG)}
                  icon={<PackIcon size={24} />}
                  title={d.packsEntry.title}
                  description={d.packsEntry.line}
                  cta={d.packsEntry.cta}
                />
              )}
            </>
          ),
        }}
        popular={{
          title: d.popularTitle,
          items: popular.map((p) => ({ href: p.href, title: p.title, meta: d.journeys[p.journey].title })),
        }}
        promise={{ label: d.promiseLabel, text: d.promise }}
      />
      <HubMedia />
    </>
  );
}

/**
 * The hub's recording, by role (`content/media-registry.json`, page `docs`, role `lead`), with the guide it shows. The hub template
 * of @krizaka/ui has no media slot (a krizaka-ui change asks for one): until then it sits under the hub, on its width.
 */
function HubMedia() {
  const { t } = useTranslation();
  const m = t.docs.media;
  if (sectionMedia("docs", "lead").length === 0) {
    return null;
  }
  return (
    <section
      data-testid="docs-media"
      className="mx-auto grid max-w-6xl items-center gap-10 px-4 py-16 sm:px-5 md:grid-cols-[1fr_18rem]"
    >
      <div className="flex flex-col gap-6">
        <LandingSectionHeading eyebrow={t.docs.journeys.guides.title} title={m.title} lead={m.lead} />
        <Link href={pageHref("studio-guide")} className="font-medium text-fg-accent underline-offset-4 hover:underline">
          {m.cta}
        </Link>
      </div>
      <SectionMedia page="docs" role="lead" />
    </section>
  );
}
