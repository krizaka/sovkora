/**
 * @file docs-shared.ts
 * @description What the documentation's pages and its client components share: the journeys, the shape of an entry, the
 * address of a page and the search. Pure — no file access (that is `docs.ts`, server side).
 */

import type { ClipId } from "@/core/clips/clips";
import { FOLDED_JOURNEY, GROUP_IDS, JOURNEY_IDS, type GroupId, type JourneyId } from "@/core/constants/docs-journeys";

export { FOLDED_JOURNEY, GROUP_IDS, JOURNEY_IDS };
export type { GroupId, JourneyId };

export interface DocEntry {
  slug: string;
  journey: JourneyId;
  group: GroupId | null;
  order: number;
  title: string;
  intro: string;
  audience: string;
  /** Path of the source file in the Sovkora repository (null for a page that is not a markdown file). */
  source: string | null;
  /** Written from the code by `orazaka docs sync`. */
  generated: boolean;
  /** A recording of `core/clips` shown under the title (null for none). */
  clip: ClipId | null;
  /** The synced markdown (null for a page that is not a markdown file). */
  file: string | null;
}

export const REPO = "https://github.com/krizaka/orazaka";

/** Where "Edit on GitHub" leads: the source markdown (for a generated page, the generated file the source writes). */
export function editUrl(entry: DocEntry): string | null {
  if (!entry.source) return null;
  return `${REPO}/${entry.generated ? "blob" : "edit"}/main/${entry.source}`;
}

/** The packs catalogue: the docs' `packs` section, featured on the hub and linked from the sidebar. */
export const PACKS_SLUG = "packs";

export const pageHref = (slug: string) => `/docs/${slug}`;

/** What the hub's search reads: a page's title, intro and headings. */
export interface SearchEntry {
  slug: string;
  title: string;
  intro: string;
  journey: JourneyId;
  headings: { id: string; label: string }[];
}

/** The sidebar's data: every page, in reading order, with just what the links need. */
export interface NavItem {
  slug: string;
  title: string;
  journey: JourneyId;
  group: GroupId | null;
}

/** Pages whose words match every word of `query`, the best first: a title hit, then a heading, then the intro. */
export function searchDocs(
  index: readonly SearchEntry[],
  query: string,
  limit = 8,
): { slug: string; id: string | null; label: string; meta: string }[] {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  const hits: { score: number; slug: string; id: string | null; label: string; meta: string }[] = [];
  for (const e of index) {
    const title = e.title.toLowerCase();
    const intro = e.intro.toLowerCase();
    const inPage = (text: string) => words.every((w) => text.includes(w));
    if (inPage(title)) hits.push({ score: 3, slug: e.slug, id: null, label: e.title, meta: e.journey });
    else if (inPage(`${title} ${intro}`))
      hits.push({ score: 1, slug: e.slug, id: null, label: e.title, meta: e.journey });
    for (const h of e.headings) {
      if (inPage(`${title} ${h.label.toLowerCase()}`) && !inPage(title))
        hits.push({ score: 2, slug: e.slug, id: h.id, label: `${e.title} › ${h.label}`, meta: e.journey });
    }
  }
  return [...hits]
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map(({ score: _score, ...rest }) => rest);
}
