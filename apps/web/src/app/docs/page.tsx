import type { Metadata } from "next";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { DocsHub, type HubJourney } from "@/features/docs/components/DocsHub";
import {
  JOURNEY_IDS,
  findPage,
  pageHref,
  pagesOf,
  readDoc,
  searchIndex,
  splitSegments,
} from "@/features/docs/utils/docs";
import { translations } from "@/core/context/translations";

const m = translations.en.docs;

export const metadata: Metadata = { title: m.meta, description: m.metaDescription };

/** The commands of the quick start, as the CLI reference (generated from the CLI's code) writes them. */
function quickstart(): string {
  const cli = readDoc("cli");
  const block = cli && splitSegments(cli.body).find((s) => s.kind === "diagram" && s.lang === "cli-replay");
  return block?.kind === "diagram" ? block.source.trim() : "npx sovkora install\nnpx sovkora start\nnpx sovkora dev";
}

/** `/docs` — the hub (the packs section has its own entry block after the quickstart, not a card) of the documentation, organised by what the reader came to do. */
export default function DocsHubPage() {
  // A journey with no published page has no card, and a popular page that is not published is not listed (the public export publishes a part of the pages).
  const journeys: HubJourney[] = JOURNEY_IDS.filter((id) => id !== "packs").flatMap((id) => {
    const pages = pagesOf(id);
    return pages.length === 0
      ? []
      : [
          {
            id,
            href: pageHref(pages[0].slug),
            links: pages.slice(0, 4).map((p) => ({ href: pageHref(p.slug), label: p.title })),
          },
        ];
  });
  const popular = ["101", "concepts", "studio-guide", "api"]
    .map((s) => findPage(s))
    .filter((p) => p !== null)
    .map((p) => ({ href: pageHref(p.slug), title: p.title, journey: p.journey }));
  return (
    <>
      <LandingNav onHome={false} />
      <main id="main" className="min-h-screen bg-surface-0 text-fg">
        <DocsHub
          index={searchIndex()}
          journeys={journeys}
          quickstart={quickstart()}
          popular={popular}
          packs={findPage("packs") !== null}
        />
      </main>
      <LandingFooter />
    </>
  );
}
