/**
 * @file docs.ts
 * @description Reads the documentation published on this site: `content/docs/index.json` and its markdown, written by
 * `scripts/sync-docs.mjs` from the Sovkora repository (never hand-edited). Pages are organised by reader journey —
 * Get started → Use it → Run it → Build on it → Mobile — and each has a previous and a next page in that order.
 */
import fs from "node:fs";
import path from "node:path";
import {
  GROUP_IDS,
  JOURNEY_IDS,
  REPO,
  type DocEntry,
  type GroupId,
  type JourneyId,
  type NavItem,
  type SearchEntry,
} from "@/features/docs/utils/docs-shared";
import { parseSource, type BlockNode, type InlineNode } from "@krizaka/ui/markdown";

export { JOURNEY_IDS, REPO, editUrl, pageHref, searchDocs } from "@/features/docs/utils/docs-shared";
export type { DocEntry, GroupId, JourneyId, NavItem, SearchEntry } from "@/features/docs/utils/docs-shared";
const DIR = path.join(process.cwd(), "content", "docs");
const read = <T>(name: string): T => JSON.parse(fs.readFileSync(path.join(DIR, name), "utf8")) as T;

/** Every page of the documentation, in reading order: by journey, then group (integrate before internals), then order. */
export function listPages(): DocEntry[] {
  const groupRank = (g: GroupId | null) => (g ? GROUP_IDS.indexOf(g) : -1);
  return read<DocEntry[]>("index.json").sort(
    (a, b) =>
      JOURNEY_IDS.indexOf(a.journey) - JOURNEY_IDS.indexOf(b.journey) ||
      groupRank(a.group) - groupRank(b.group) ||
      a.order - b.order,
  );
}

/** The pages that are a synced markdown file. */
export function listDocs(): DocEntry[] {
  return listPages().filter((d) => d.file);
}

export function findPage(slug: string): DocEntry | null {
  return listPages().find((d) => d.slug === slug) ?? null;
}

export function readDoc(slug: string): { entry: DocEntry; body: string } | null {
  const entry = listDocs().find((d) => d.slug === slug);
  return entry ? { entry, body: fs.readFileSync(path.join(DIR, entry.file!), "utf8") } : null;
}

export function pagesOf(journey: JourneyId): DocEntry[] {
  return listPages().filter((d) => d.journey === journey);
}

/** The page before and the page after, in reading order across the whole documentation. */
export function neighbours(slug: string): { previous: DocEntry | null; next: DocEntry | null } {
  const pages = listPages();
  const i = pages.findIndex((d) => d.slug === slug);
  return { previous: i > 0 ? pages[i - 1] : null, next: i >= 0 && i < pages.length - 1 ? pages[i + 1] : null };
}

// ── Links ────────────────────────────────────────────────────────────────────────────────────────

const SCHEME = /^([a-z][a-z0-9+.-]*:|#|\/)/i;

/** Resolves `href`, written in the source file `from` (a path of the Sovkora repository), to its place on this site. */
export function resolveLink(
  href: string,
  from: string,
  sources: Readonly<Record<string, string>>,
): { href: string; internal: boolean } | null {
  if (SCHEME.test(href)) return { href, internal: !/^[a-z][a-z0-9+.-]*:/i.test(href) };
  const [rawPath, fragment] = href.split("#");
  const target = path.posix.normalize(path.posix.join(path.posix.dirname(from), rawPath));
  if (target.startsWith("..")) return null;
  const hit = Object.entries(sources).find(([src]) => src.toLowerCase() === target.toLowerCase());
  if (hit) return { href: `/docs/${hit[1]}${fragment ? `#${fragment}` : ""}`, internal: true };
  return { href: `${REPO}/blob/main/${target}${fragment ? `#${fragment}` : ""}`, internal: false };
}

/**
 * The markdown of a page ready to render: links to other docs lead to their page here, links to anything else to GitHub
 * (never a dead link), a link out of the repository is plain text, relative images come from the repository, and GitHub's
 * `> [!NOTE]` callouts read as a bold lead-in. Fenced code is left alone.
 */
export function prepareBody(body: string, from: string | null, sources: Readonly<Record<string, string>>): string {
  const origin = from ?? "README.md";
  let fence = false;
  const out = body.split("\n").map((line) => {
    if (/^ {0,3}(```|~~~)/.test(line)) fence = !fence;
    if (fence || /^ {0,3}(```|~~~)/.test(line)) return line;
    return line
      .replace(
        /(!?)\[([^\]]{0,300})\]\(([^)\s]{1,500})(\s+"[^"]{0,300}")?\)/g,
        (m, bang: string, text: string, href: string, title: string | undefined) => {
          if (bang) {
            if (/^[a-z][a-z0-9+.-]*:/i.test(href)) return m;
            const target = path.posix.normalize(path.posix.join(path.posix.dirname(origin), href));
            return `![${text}](https://raw.githubusercontent.com/krizaka/orazaka/main/${target})`;
          }
          const r = resolveLink(href, origin, sources);
          return r ? `[${text}](${r.href}${title ?? ""})` : text;
        },
      )
      .replace(
        /^(\s*>\s*)\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*$/i,
        (_m, lead: string, kind: string) => `${lead}**${kind[0]}${kind.slice(1).toLowerCase()}.**`,
      );
  });
  return out.join("\n").replace(/^(\s*>\s*\*\*[A-Za-z]+\.\*\*)\n\s*>\s*/gm, "$1 ");
}

export function sourcesMap(): Record<string, string> {
  return read<Record<string, string>>("sources.json");
}

// ── Segments: markdown and diagram fences ────────────────────────────────────────────────────────

export const DIAGRAM_LANGS = [
  "architecture",
  "pipeline",
  "sequence",
  "states",
  "cli-replay",
  "cli-journey",
  "cli-commands",
  "pack-index",
  "pack",
] as const;
export type DiagramLang = (typeof DIAGRAM_LANGS)[number];
export type Segment = { kind: "markdown"; text: string } | { kind: "diagram"; lang: DiagramLang; source: string };

/** Splits a page into its markdown and its schema blocks (the fenced `architecture`, `pipeline`, `sequence`, `states`, `cli-*`). */
export function splitSegments(body: string): Segment[] {
  const segments: Segment[] = [];
  let text: string[] = [];
  let diagram: { lang: DiagramLang; lines: string[] } | null = null;
  let fence: string | null = null;
  const flush = () => {
    const t = text.join("\n");
    if (t.trim()) segments.push({ kind: "markdown", text: t });
    text = [];
  };
  for (const line of body.split("\n")) {
    const open = /^ {0,3}(```|~~~)\s*([\w-]*)/.exec(line);
    if (diagram) {
      if (/^ {0,3}```\s*$/.test(line)) {
        segments.push({ kind: "diagram", lang: diagram.lang, source: diagram.lines.join("\n") });
        diagram = null;
      } else diagram.lines.push(line);
    } else if (fence) {
      text.push(line);
      if (line.trim().startsWith(fence)) fence = null;
    } else if (open && (DIAGRAM_LANGS as readonly string[]).includes(open[2]) && open[1] === "```") {
      flush();
      diagram = { lang: open[2] as DiagramLang, lines: [] };
    } else {
      if (open) fence = open[1];
      text.push(line);
    }
  }
  flush();
  return segments;
}

// ── Contents ─────────────────────────────────────────────────────────────────────────────────────

const plain = (nodes: readonly InlineNode[]): string => nodes.map(textOf).join("");

function textOf(n: InlineNode): string {
  if ("value" in n && typeof n.value === "string") return n.value;
  return "children" in n ? plain(n.children as InlineNode[]) : "";
}

/**
 * The `##` headings of the markdown segments — the id `Markdown` gives each, and its words — plus a pack's `###`
 * heading (the one right before its `pack` block), so the contents list one entry per pack.
 */
export function tocOf(segments: readonly Segment[]): { id: string; label: string }[] {
  const items: { id: string; label: string }[] = [];
  segments.forEach((s, i) => {
    if (s.kind !== "markdown") return;
    const next = segments[i + 1];
    const packHeading = next?.kind === "diagram" && next.lang === "pack";
    const blocks = parseSource(s.text, { headingIds: true }) as BlockNode[];
    blocks.forEach((b, j) => {
      if (b.type !== "heading" || !b.id) return;
      const wanted = b.depth === 2 || (packHeading && b.depth === 3 && j === blocks.length - 1);
      if (wanted) items.push({ id: b.id, label: plain(b.children as InlineNode[]).replace(/^\d+[.)]\s+/, "") });
    });
  });
  return items;
}

/** What `llms-full.txt` and the search read: the page's words, the schema blocks as their source text. */
export function plainText(body: string): string {
  return splitSegments(body)
    .map((s) => (s.kind === "markdown" ? s.text : `(${s.lang})\n${s.source}`))
    .join("\n\n");
}

export function searchIndex(): SearchEntry[] {
  return listPages().map((d) => {
    const doc = readDoc(d.slug);
    const headings = doc ? tocOf(splitSegments(doc.body)) : [];
    return { slug: d.slug, title: d.title, intro: d.intro, journey: d.journey, headings };
  });
}

export function navItems(): NavItem[] {
  return listPages().map(({ slug, title, journey, group }) => ({ slug, title, journey, group }));
}
