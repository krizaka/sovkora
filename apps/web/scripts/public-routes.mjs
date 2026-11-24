import { join } from "node:path";

/**
 * The public export of Sovkora (krizaka-internal#4625, parent #4148): the pages a visitor may read while the product has no
 * accounts on its public site. One list, read by the build script (which stages exactly these page files), the export check
 * and their tests. A page is added here AND reviewed for server parts: nothing else is ever exported.
 */
export const PUBLIC_LOCALES = ["fr", "en"];

/**
 * The documentation pages the export publishes (slugs of `content/docs/index.json`), and only these: a page enters this list once the
 * shared guard is green on it. The pages left out carry what a public site must not (the retired code name in a path or a command,
 * « sign in », the dev. host, model prices): they are fixed at their source in the repository's `docs/`, then added here.
 */
export const PUBLIC_DOCS = ["studio-guide", "security"];

/** The documentation index of the export: the entries of `content/docs/index.json` that are published, in their order. */
export const publicDocsIndex = (entries) => entries.filter((entry) => PUBLIC_DOCS.includes(entry.slug));

/** The docs sources map (`content/docs/sources.json`, source path → slug) of the export: a link to a page that is not published is not a link there. */
export const publicDocsSources = (sources) =>
  Object.fromEntries(Object.entries(sources).filter(([, slug]) => PUBLIC_DOCS.includes(slug)));

/** The pages under `/<locale>/` that are exported ("" is the home). */
export const PUBLIC_PAGES = ["", "docs", ...PUBLIC_DOCS.map((slug) => `docs/${slug}`), "pricing", "privacy"];

/** The routes the export holds, as the check reads them in the output folder (`/fr`, `/fr/docs`, …). */
export const PUBLIC_ROUTES = PUBLIC_LOCALES.flatMap((locale) =>
  PUBLIC_PAGES.map((page) => (page ? `/${locale}/${page}` : `/${locale}`)),
);

/**
 * The files the staged project links, `at` (relative to its `app/`, one project per language) ← `from` (relative to `apps/web`). The pages are the REAL
 * page files of the dev app, except the two that read a cookie or the billing service (the home, the pricing):
 * those have a static twin in `public-build/`.
 */
export const STAGED_FILES = [
  { at: "layout.tsx", from: join("public-build", "root-layout.tsx") },
  { at: "page.tsx", from: join("public-build", "home-page.tsx") },
  { at: join("docs", "page.tsx"), from: join("src", "app", "docs", "page.tsx") },
  { at: join("docs", "[slug]", "page.tsx"), from: join("src", "app", "docs", "[slug]", "page.tsx") },
  { at: join("pricing", "page.tsx"), from: join("public-build", "pricing-page.tsx") },
  { at: join("privacy", "page.tsx"), from: join("src", "app", "privacy", "page.tsx") },
  { at: join("privacy", "layout.tsx"), from: join("src", "app", "privacy", "layout.tsx") },
];

/** What the staged project links, unchanged, from `apps/web`: the code the pages import, the icons, the build files (`content/` is staged apart: its docs index is the published pages only). */
export const PUBLIC_SHARED_ENTRIES = ["public-build", "public", "package.json", "postcss.config.mjs"];
