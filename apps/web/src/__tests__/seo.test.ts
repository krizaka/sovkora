/** @jest-environment node */
import robots from "@/app/robots";
import sitemap from "@/app/sitemap";
import { proxy } from "@/proxy";
import { NextRequest } from "next/server";
import { indexable, isDevAddress, NOINDEX_HEADER } from "@/core/config/indexing";
import {
  CRAWLERS,
  KRIZAKA_URL,
  SITE_DESCRIPTION,
  SITE_TITLE,
  llmsFullText,
  llmsText,
  ORG_ID,
  publicPaths,
  SITE_URL,
  siteJsonLd,
} from "@/app/_seo/seo";
// eslint-disable-next-line no-restricted-imports -- the shared family guard lives in scripts/, outside src/
import { auditLlms, auditRobots, auditSitemap } from "../../scripts/check-seo-family.mjs";

/** MetadataRoute.Robots as the text Next serves. */
function robotsText(r: ReturnType<typeof robots>): string {
  const rules = Array.isArray(r.rules) ? r.rules : [r.rules];
  const groups = rules.map((rule) =>
    [
      `User-Agent: ${rule.userAgent}`,
      `Allow: ${rule.allow}`,
      ...([] as string[]).concat(rule.disallow ?? []).map((d) => `Disallow: ${d}`),
    ].join("\n"),
  );
  return `${groups.join("\n\n")}\n\nSitemap: ${r.sitemap}\n`;
}

describe("search engines and AI assistants (the Krizaka family contract)", () => {
  it("robots.txt welcomes every AI crawler, keeps the workspace private and names the sitemap", () => {
    const text = robotsText(robots());
    expect(auditRobots(text, { host: SITE_URL })).toEqual([]);
    for (const bot of CRAWLERS) expect(text).toContain(`User-Agent: ${bot}`);
    expect(text).toContain("Disallow: /dashboard");
  });

  it("sitemap.xml lists the home, the Mac page and every published doc on the host", () => {
    const entries = sitemap();
    const xml = `<urlset>${entries.map((e) => `<url><loc>${e.url}</loc><lastmod>${new Date(e.lastModified as Date).toISOString()}</lastmod></url>`).join("")}</urlset>`;
    expect(auditSitemap(xml, { host: SITE_URL, minUrls: 10 })).toEqual([]);
    expect(entries.map((e) => e.url)).toContain(`${SITE_URL}/desktop`);
    expect(publicPaths()).toContain("/docs/architecture");
    expect(publicPaths()).toEqual(expect.arrayContaining(["/docs/mobile", "/docs/models", "/desktop"]));
    expect(entries.map((e) => e.url)).toContain(`${SITE_URL}/docs/models`);
  });

  it("llms.txt and llms-full.txt follow llmstxt.org, name this host and link the publisher", () => {
    expect(auditLlms(llmsText(), { host: SITE_URL })).toEqual([]);
    expect(auditLlms(llmsFullText(), { host: SITE_URL, full: true })).toEqual([]);
    expect(llmsText()).toContain(`[Models](${SITE_URL}/docs/models)`);
    expect(llmsFullText().length).toBeGreaterThan(llmsText().length * 5);
  });

  it("the JSON-LD names the Krizaka Organization on www as publisher of the site and the application", () => {
    const graph = siteJsonLd()["@graph"] as { "@type": string; publisher: { "@id": string; url: string } }[];
    expect(ORG_ID).toBe(`${KRIZAKA_URL}/#organization`);
    expect(graph.map((n) => n.publisher["@id"])).toEqual([ORG_ID, ORG_ID]);
    expect(graph.find((n) => n["@type"] === "SoftwareApplication")).toMatchObject({
      applicationCategory: "BusinessApplication",
    });
  });

  it.each(["/robots.txt", "/sitemap.xml", "/llms.txt", "/llms-full.txt", "/opengraph-image"])(
    "the sign-in proxy lets crawlers read %s",
    (path) => {
      const res = proxy(new NextRequest(`${SITE_URL}${path}`));
      expect(res.headers.get("x-middleware-next")).toBe("1");
    },
  );

  describe("dev.* is never indexed, www is (orazaka-web-client#262)", () => {
    const at = (host: string, path: string) => proxy(new NextRequest(`https://${host}${path}`, { headers: { host } }));
    const keep = { dev: process.env.APP_DEV_HOST, flag: process.env.APP_SEARCH_INDEXING };
    beforeEach(() => {
      // No configuration at all: the name alone must close a dev address.
      delete process.env.APP_DEV_HOST;
      delete process.env.APP_SEARCH_INDEXING;
    });
    afterAll(() => {
      for (const [k, v] of [
        ["APP_DEV_HOST", keep.dev],
        ["APP_SEARCH_INDEXING", keep.flag],
      ] as const) {
        if (v === undefined) delete process.env[k];
        else process.env[k] = v;
      }
    });

    it.each(["dev.orazaka.com", "DEV.orazaka.com:443", "dev.example.org"])(
      "%s: robots.txt is Disallow: / and every answer says noindex",
      async (host) => {
        expect(isDevAddress(host, {})).toBe(true);
        const res = at(host, "/robots.txt");
        expect(await res.text()).toBe("User-agent: *\nDisallow: /\n");
        expect(res.headers.get("x-robots-tag")).toBe(NOINDEX_HEADER);
        for (const path of ["/", "/docs", "/llms.txt", "/sitemap.xml", "/api/healthz"]) {
          expect(`${path} ${at(host, path).headers.get("x-robots-tag")}`).toBe(`${path} ${NOINDEX_HEADER}`);
        }
      },
    );

    it("a deployment whose own address is dev.* closes itself: robots, sitemap and the pages' meta", () => {
      expect(indexable({ APP_PUBLIC_URL: "https://dev.orazaka.com" })).toBe(false);
      // The code-name host is closed too (wave 1, krizaka-internal#2630): the public face is the commercial domain.
      expect(indexable({ APP_PUBLIC_URL: "https://www.orazaka.com" })).toBe(false);
      expect(indexable({ APP_PUBLIC_URL: "https://www.sovkora.com" })).toBe(true);
      expect(indexable({ APP_PUBLIC_URL: "not a url" })).toBe(true);
    });

    it("www.orazaka.com is not a dev address, yet it is a code-name host: noindex header; an unconfigured build still serves the sitemap", async () => {
      expect(isDevAddress("www.orazaka.com", {})).toBe(false);
      expect(isDevAddress("devtools.orazaka.com", {})).toBe(false);
      const res = at("www.orazaka.com", "/");
      expect(res.headers.get("x-robots-tag")).toBe(NOINDEX_HEADER);
      const r = robots();
      expect(r.sitemap).toBe(`${SITE_URL}/sitemap.xml`);
      expect(sitemap().length).toBeGreaterThan(0);
    });
  });

  it("says what it does: the description and the featureList carry RAG, LLM and MCP, and the description stays short", () => {
    expect(SITE_DESCRIPTION).toMatch(/LLM/);
    expect(SITE_DESCRIPTION).toMatch(/MCP/);
    expect(SITE_DESCRIPTION).not.toMatch(/absolute|100 ?%|guarantee/i);
    const software = siteJsonLd()["@graph"].find((n) => n["@type"] === "SoftwareApplication") as {
      featureList: string[];
    };
    expect(software.featureList.join(" ")).toMatch(/\(RAG\).*\(LLM\).*\(MCP\)/);
    expect(software.featureList).toHaveLength(6);
  });

  it("claims no Windows build: the product runs on macOS and Linux", () => {
    expect(JSON.stringify(siteJsonLd())).not.toMatch(/windows/i);
  });

  it("the title and description name the product and say what it is, once, within what a result shows", () => {
    expect(SITE_TITLE).toMatch(/^Sovkora — /);
    expect(SITE_TITLE).toMatch(/open-source/i);
    expect(SITE_TITLE).not.toMatch(/Krizaka.*\|.*Krizaka/);
    expect(SITE_TITLE.length).toBeLessThanOrEqual(62);
    expect(SITE_DESCRIPTION.length).toBeLessThanOrEqual(160);
    const app = (siteJsonLd()["@graph"] as { "@type": string; sameAs?: string[] }[]).find(
      (n) => n["@type"] === "SoftwareApplication",
    );
    expect(app?.sameAs).toContain("https://github.com/krizaka/orazaka");
  });
});
