/** @jest-environment node */
import assert from "node:assert/strict";
// eslint-disable-next-line no-restricted-imports -- the shared family guard lives in scripts/, outside src/
import {
  auditRobots,
  auditSitemap,
  auditLlms,
  auditHtml,
  auditHeaders,
  AI_CRAWLERS,
  ORG_ID,
} from "../../scripts/check-seo-family.mjs";

const HOST = "https://dev.example.com";
const robots = AI_CRAWLERS.map((b) => `User-Agent: ${b}\nAllow: /\n`).join("\n") + `\nSitemap: ${HOST}/sitemap.xml\n`;
const page = ({
  canonical = `${HOST}/`,
  ld,
  robotsMeta = "index, follow",
  image = "https://dev.example.com/og.png",
}: { canonical?: string; ld?: string; robotsMeta?: string; image?: string } = {}) => `<html><head><title>Example</title>
<meta name="description" content="A description long enough to be useful to a search engine and its readers."/>
<link rel="canonical" href="${canonical}"/><meta name="robots" content="${robotsMeta}"/>
<meta property="og:title" content="Example"/><meta property="og:description" content="d"/>${image ? `<meta property="og:image" content="${image}"/>` : ""}<meta name="twitter:card" content="summary_large_image"/>
<script type="application/ld+json">${ld ?? JSON.stringify({ "@context": "https://schema.org", "@type": "SoftwareApplication", name: "X", publisher: { "@id": ORG_ID } })}</script></head></html>`;
const opts = { host: HOST, index: true, types: ["SoftwareApplication"], home: true };

test("a complete indexed site passes", () => {
  assert.deepEqual(auditRobots(robots, { host: HOST }), []);
  assert.deepEqual(auditHtml(page(), "/", opts), []);
  assert.deepEqual(auditHeaders(new Headers(), { index: true }), []);
});

test("robots.txt: a missing AI crawler, a missing Sitemap line and a blanket Disallow are caught", () => {
  assert.ok(
    auditRobots(robots.replace("User-Agent: GPTBot\nAllow: /\n", ""), { host: HOST }).some((p) => p.includes("GPTBot")),
  );
  assert.ok(auditRobots("User-Agent: *\nAllow: /", { host: HOST }).some((p) => p.includes("Sitemap")));
  assert.ok(
    auditRobots(`${robots}\nUser-Agent: *\nDisallow: /\n`, { host: HOST }).some((p) => p.includes("must not Disallow")),
  );
});

test("a site that is not indexed must say so in robots, meta and header", () => {
  const off = `User-Agent: *\nDisallow: /\n`;
  assert.deepEqual(auditRobots(off, { host: HOST, index: false }), []);
  // a site that is not indexed advertises nothing: a sitemap line is a finding
  assert.ok(
    auditRobots(`${off}Sitemap: ${HOST}/sitemap.xml\n`, { host: HOST, index: false }).some((p) =>
      p.includes("sitemap"),
    ),
  );
  assert.ok(auditRobots(robots, { host: HOST, index: false }).length > 0);
  assert.ok(auditHtml(page(), "/", { ...opts, index: false }).some((p) => p.includes("noindex")));
  assert.deepEqual(auditHtml(page({ robotsMeta: "noindex, nofollow" }), "/", { ...opts, index: false }), []);
  assert.ok(auditHeaders(new Headers(), { index: false }).length === 1);
});

test("sitemap.xml: a URL on another host is caught", () => {
  const xml = `<urlset><url><loc>${HOST}/</loc><lastmod>2026-10-14</lastmod></url><url><loc>https://other.com/x</loc></url></urlset>`;
  assert.ok(auditSitemap(xml, { host: HOST }).some((p) => /other\.com/.test(p)));
  assert.deepEqual(
    auditSitemap(`<urlset><url><loc>${HOST}/</loc><lastmod>2026-10-14</lastmod></url></urlset>`, { host: HOST }),
    [],
  );
});

test("llms.txt: shape and the way back to the family", () => {
  const ok = `# Example\n\n> What it is.\n\n## Pages\n\n- [Home](${HOST}/): home\n- [Krizaka](https://www.krizaka.com): the company\n`;
  assert.deepEqual(auditLlms(ok, { host: HOST }), []);
  assert.ok(auditLlms("Example\n", { host: HOST }).length >= 3);
});

test("pages: wrong canonical, no image, JSON-LD that does not lead to Krizaka are caught", () => {
  assert.ok(auditHtml(page({ canonical: "https://elsewhere.com/" }), "/", opts).some((p) => p.includes("canonical")));
  assert.ok(auditHtml(page({ image: "" }), "/", opts).some((p) => p.includes("og:image")));
  const orphan = JSON.stringify({ "@context": "https://schema.org", "@type": "SoftwareApplication", name: "X" });
  assert.ok(auditHtml(page({ ld: orphan }), "/", opts).some((p) => p.includes("Krizaka Organization")));
  assert.ok(auditHtml(page({ ld: "{nope" }), "/", opts).some((p) => p.includes("does not parse")));
});

test("an operator console: no sitemap needed, but it must say noindex", async () => {
  const { auditConsoleHtml } = await import("../../scripts/check-seo-family.mjs");
  assert.deepEqual(auditRobots("User-Agent: *\nDisallow: /\n", { host: HOST, index: false, sitemap: false }), []);
  assert.deepEqual(auditConsoleHtml('<meta name="robots" content="noindex, nofollow"/>'), []);
  assert.equal(auditConsoleHtml("<html></html>").length, 1);
});
