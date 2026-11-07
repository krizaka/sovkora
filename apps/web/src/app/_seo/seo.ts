/**
 * @file seo.ts
 * @description Search engines and AI assistants (the Krizaka family contract, scripts/check-seo-family.mjs): the public
 * origin, the crawlers the whole family welcomes, the JSON-LD that leads back to the Krizaka Organization, and
 * llms.txt / llms-full.txt (llmstxt.org). Structure only — the product words are the landing's own messages.
 */
import { listPages, plainText, readDoc } from "@/features/docs/utils/docs";

/** A page's words for `llms-full.txt`: its markdown, the schema blocks as their source text. */
function plainDoc(slug: string): string | undefined {
  const doc = readDoc(slug);
  return doc ? plainText(doc.body).trim() : undefined;
}

/** The public origin of this site (www.orazaka.com: the real app, sign-up closed). */
export const SITE_URL = "https://www.orazaka.com";
export const KRIZAKA_URL = "https://www.krizaka.com";
export const REPO_URL = "https://github.com/krizaka/orazaka";
/** The Krizaka Organization, defined by www.krizaka.com: every JSON-LD node of the family points back to it. */
export const ORG_ID = `${KRIZAKA_URL}/#organization`;

export const SITE_NAME = "Sovkora";
/** What the search result says: the name first, then what it is (a query for « Sovkora » or « sovereign AI platform »). */
export const SITE_TITLE = "Sovkora — open-source sovereign AI platform, self-hosted";
export const SITE_DESCRIPTION =
  "Sovkora by Krizaka (Montréal): a sovereign AI platform. Your LLM, local or hosted, MCP tools, agents that ask first, Studios, every result traced to its run.";
/** The same two lines in French (the page's `<title>` and Open Graph on `/fr`): the product's one line, « L'IA qui reste chez vous ». */
export const SITE_TITLE_FR = "Sovkora : l'IA qui reste chez vous";
export const SITE_DESCRIPTION_FR =
  "Sovkora, de Krizaka (Montréal) : l'IA qui reste chez vous. Vos modèles, vos machines : vos données ne sortent pas.";

/** What the product does, in the plain words of the home's « What Sovkora does » section (each one is true of what runs; RAG is in development). */
export const FEATURE_LIST = [
  "Answers grounded in your own documents (RAG), in development",
  "The language models (LLM) you choose, local or hosted",
  "Connects to your tools through the open Model Context Protocol (MCP)",
  "Agents that use tools and ask before acting",
  "Studios for images, video and checked documents, priced before they run",
  "Every result traced to its run",
];

/** The search bots and AI crawlers the whole family welcomes — the same list on every site. */
export const CRAWLERS = [
  "Googlebot",
  "Google-Extended",
  "Bingbot",
  "GPTBot",
  "OAI-SearchBot",
  "ChatGPT-User",
  "PerplexityBot",
  "Perplexity-User",
  "Claude-SearchBot",
  "Claude-User",
  "ClaudeBot",
  "anthropic-ai",
  "Applebot",
  "Applebot-Extended",
  "CCBot",
] as const;

/** What a crawler may not walk into: the signed-in workspace, the BFF and the account forms. */
export const PRIVATE_PATHS = [
  "/api/",
  "/dashboard",
  "/chat",
  "/jobs",
  "/usage",
  "/settings",
  "/interceptions",
  "/login",
  "/register",
  "/verify",
  "/forgot-password",
];

/** Every public page, in the order of the sitemap. */
export function publicPaths(): string[] {
  return [
    "/",
    "/desktop",
    "/pricing",
    "/open-source",
    "/docs",
    ...listPages().map((d) => `/docs/${d.slug}`),
    "/privacy",
    "/terms",
  ];
}

const ORG = { "@type": "Organization", "@id": ORG_ID, name: "Krizaka", url: KRIZAKA_URL };

/** The site and the product it presents, both published by the Krizaka Organization. */
export function siteJsonLd() {
  return {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "WebSite",
        "@id": `${SITE_URL}/#website`,
        url: `${SITE_URL}/`,
        name: SITE_NAME,
        inLanguage: ["en", "fr"],
        publisher: ORG,
      },
      {
        "@type": "SoftwareApplication",
        "@id": `${SITE_URL}/#software`,
        name: SITE_NAME,
        url: `${SITE_URL}/`,
        description: SITE_DESCRIPTION,
        applicationCategory: "BusinessApplication",
        operatingSystem: "Linux, macOS",
        isAccessibleForFree: true,
        featureList: FEATURE_LIST,
        license: "https://www.apache.org/licenses/LICENSE-2.0",
        codeRepository: REPO_URL,
        sameAs: [REPO_URL],
        publisher: ORG,
        isPartOf: { "@id": `${SITE_URL}/#website` },
      },
    ],
  };
}

// A deployed product is independent: llms.txt names its publisher and nothing else of the family — no other product.
const familySection = () =>
  `## Publisher\n\n- [Krizaka](${KRIZAKA_URL}): the company that publishes Sovkora, an open-source software studio in Montréal\n\nSitemap: ${SITE_URL}/sitemap.xml\n`;

export function llmsText(): string {
  const docs = listPages().map((d) => `- [${d.title}](${SITE_URL}/docs/${d.slug}): ${d.intro}`);
  return `# ${SITE_NAME}

> ${SITE_DESCRIPTION} A product of Krizaka (Montréal), open source under Apache-2.0. Sign-in is closed on this development site.

## Key pages

- [Home](${SITE_URL}/): what Sovkora is
- [Mobile app](${SITE_URL}/docs/mobile): recordings and screenshots of the iOS and Android app
- [Sovkora for Mac](${SITE_URL}/desktop): the Sovkora Desktop test build for Apple Silicon — what it does today, requirements, download
- [Pricing and editions](${SITE_URL}/pricing): Community (open source, runs today), Business (soon), and Krizaka's Enterprise offer for anything custom
- [Open source](${SITE_URL}/open-source): the public repositories, the Apache-2.0 licence line, self-hosting and how to contribute
- [Packs](${SITE_URL}/docs/packs): the catalogue of installable offers, what each pack adds to the Studios, and how to write one
- [Models](${SITE_URL}/docs/models): the models Sovkora supports — what each does, where it runs, its licence and context window
- [Talk to the team](${KRIZAKA_URL}/en/contact?topic=sovkora): the contact form lives on krizaka.com

## Documentation

- [Documentation hub](${SITE_URL}/docs): architecture, packs and Studios, the API, administration, the CLI
${docs.join("\n")}
- Full text of the documentation: ${SITE_URL}/llms-full.txt

## Source

- [Sovkora repository](${REPO_URL}): the engine and its workspace (Apache-2.0)

${familySection()}`;
}

export function llmsFullText(): string {
  const docs = listPages().map(
    (d) => `\n---\n\n# ${d.title}\n\nURL: ${SITE_URL}/docs/${d.slug}\n\n${plainDoc(d.slug) ?? d.intro}\n`,
  );
  return `${llmsText().replace(/\n## Publisher[\s\S]*$/, "")}\n## Documentation (full text)\n${docs.join("")}\n---\n\n${familySection()}`;
}

export const LLMS_HEADERS = { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=3600" };
