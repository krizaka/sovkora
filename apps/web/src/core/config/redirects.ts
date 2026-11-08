/**
 * The public addresses that moved or were removed, each answered with a 308 (permanent, method kept) so no link, no
 * bookmark and no search result 404s (epic krizaka/orazaka-web-client#231). `next.config.ts` reads this list;
 * `src/__tests__/public-routes.test.ts` checks that every target exists.
 */
export interface Redirect {
  source: string;
  destination: string;
  permanent: boolean;
  has?: { type: "host"; value: string }[];
}

const LOCALE_PREFIXES = ["fr", "en"] as const;
const PACKS_DOCS = "/docs/packs";

/**
 * Addresses that are a page of the signed-in app: a config redirect would also hit a session, so the proxy answers 308
 * only to a visitor without one (`/packs` is « Plan & packs » once signed in).
 */
export const SIGNED_OUT_REDIRECTS: readonly { source: string; destination: string }[] = [
  { source: "/packs", destination: PACKS_DOCS },
];

/** Removed on 2026-10-21 (#231): feature tours, the gallery, the models catalogue, three marketing or stub docs. */
export const REMOVED_PUBLIC: readonly { source: string; destination: string }[] = [
  { source: "/features", destination: "/#features" },
  { source: "/features/:id", destination: "/#features" },
  { source: "/gallery", destination: "/#records" },
  { source: "/models", destination: "/docs/models" },
  { source: "/infrastructure", destination: "/docs/self-hosting" },
  { source: "/docs/business-playbook", destination: "/docs/101" },
  { source: "/docs/cinematic-shorts", destination: PACKS_DOCS },
  { source: "/docs/use-cases", destination: "/docs/studio-guide" },
  // Packs left the bar for the docs (2026-10-22): the catalogue is /docs/packs, whatever the locale prefix.
  ...LOCALE_PREFIXES.map((l) => ({ source: `/${l}/packs`, destination: PACKS_DOCS })),
];

/**
 * Removed on 2026-10-22 (the docs' short path, krizaka/orazaka#351): the pages that repeated the Concepts page or the
 * architecture reference. They stay generated on GitHub; the reading path no longer carries them.
 */
const CONCEPTS_PAGES = ["glossary", "core"] as const;
const ARCHITECTURE_PAGES = [
  "interceptors",
  "interface-contracts",
  "business",
  "repositories",
  "governance",
  "adrs",
] as const;
export const REMOVED_DOCS: readonly { source: string; destination: string }[] = [
  ...CONCEPTS_PAGES.map((slug) => ({ source: `/docs/${slug}`, destination: "/docs/concepts" })),
  ...ARCHITECTURE_PAGES.map((slug) => ({ source: `/docs/${slug}`, destination: "/docs/architecture" })),
  // Unpublished on 2026-10-23 (the essential only): each to the page that covers it for a reader.
  { source: "/docs/tools", destination: "/docs/api" },
  { source: "/docs/automation", destination: "/docs/worker-protocol" },
  { source: "/docs/jobs", destination: "/docs/architecture" },
  { source: "/docs/admin", destination: "/docs/self-hosting" },
  { source: "/docs/mobile-guide", destination: "/docs/mobile" },
  // The licence line moved to /open-source (krizaka/orazaka-web-client#290).
  { source: "/docs/licensing", destination: "/open-source" },
];

/** Older renames, kept (their targets are the pages that stay). */
const RENAMED: readonly { source: string; destination: string }[] = [
  // The mobile guide is a section of the documentation; the docs' pages were renamed (slugs without underscores).
  { source: "/mobile", destination: "/docs/mobile" },
  { source: "/docs/api_reference", destination: "/docs/api" },
  { source: "/docs/interface_contracts", destination: "/docs/architecture" },
  { source: "/docs/worker_protocol", destination: "/docs/worker-protocol" },
  { source: "/docs/business_implementation", destination: "/docs/101" },
  { source: "/docs/use_cases", destination: "/docs/studio-guide" },
  // The signed-in app's addresses say the section's name (docs/ux/information-architecture.md of the workspace).
  { source: "/dashboard/jobs", destination: "/jobs" },
  { source: "/profile", destination: "/settings" },
];

export const PUBLIC_REDIRECTS: readonly Redirect[] = [
  // The contact form lives on krizaka.com.
  {
    source: "/contact",
    destination: "https://www.krizaka.com/en/contact?topic=sovkora&source=sovkora-app",
    permanent: true,
  },
  {
    source: "/fr/contact",
    destination: "https://www.krizaka.com/fr/contact?topic=sovkora&source=sovkora-app",
    permanent: true,
  },
  {
    source: "/en/contact",
    destination: "https://www.krizaka.com/en/contact?topic=sovkora&source=sovkora-app",
    permanent: true,
  },
  ...[...REMOVED_PUBLIC, ...REMOVED_DOCS, ...RENAMED].map((r) => ({ ...r, permanent: true })),
  // Not on admin.orazaka.com: /billing is a section of the operator console there (src/console), served by the proxy.
  { source: "/billing", has: [{ type: "host", value: "(?!admin\\.).*" }], destination: "/usage", permanent: true },
];
