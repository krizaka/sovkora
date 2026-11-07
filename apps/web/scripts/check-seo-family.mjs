#!/usr/bin/env node
/* The Krizaka family SEO guard (krizaka/krizaka-com#SEO-FAMILY) — one file, no dependency, copied unchanged into every
   site's repository (keep the copies identical; the contract lives in krizaka-com/AGENTS.md §2.4).

   What every site of the family owes search engines and AI assistants, checked on what the server really sends:
   robots.txt (a Sitemap line on the site's host; AI crawlers allowed on an indexed site, `Disallow: /` on a site that
   is not), sitemap.xml (every <loc> on the host; hreflang alternates on the same host), llms.txt (llmstxt.org shape,
   points to the Krizaka family), and per page: a canonical that is the page on the host, Open Graph + Twitter card with
   an image, a title and a description, JSON-LD that parses, whose nodes all lead back to the Krizaka Organization
   (https://www.krizaka.com/#organization), and noindex exactly when the site is not indexed.

   Usage: node scripts/check-seo-family.mjs --base <origin served> --host <https://public-host> [--index on|off]
            [--types SoftwareApplication,WebSite] [--no-llms] [--no-sitemap] [--limit N] [--console]
   An operator console (admin.*): `--console --index off` checks only that robots.txt shuts the door and that every
   response says noindex (header and meta); it has no sitemap and no llms.txt.
   `--base` is where the pages are fetched (http://localhost:3000 in CI); `--host` is the public origin they must declare. */

export const ORG_ID = "https://www.krizaka.com/#organization";
/** @type {string[]} */
export const AI_CRAWLERS = [
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
];

const attr = (tag, name) => new RegExp(`${name}="([^"]*)"`, "i").exec(tag)?.[1];
const decode = (s) =>
  s
    .replace(/&#x27;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/&amp;/g, "&");
const tags = (html, name) => [...html.matchAll(new RegExp(`<${name}\\s[^>]*>`, "gi"))].map((m) => m[0]);
const strip = (u) => u.replace(/\/$/, "");

/** robots.txt of a site: `index` says whether it wants to be crawled.
 * @param {string} text
 * @param {{ host: string, index?: boolean, sitemap?: boolean }} opts - `sitemap: false` for an operator console, which has none
 * @returns {string[]} */
export function auditRobots(text, { host, index = true, sitemap = true }) {
  const problems = [];
  if (sitemap && index && !text.includes(`Sitemap: ${host}/sitemap.xml`))
    problems.push(`robots.txt: no "Sitemap: ${host}/sitemap.xml" line`);
  const groups = text.split(/\n\s*\n/);
  const group = (bot) => groups.find((g) => new RegExp(`^user-agent:\\s*${bot}\\s*$`, "im").test(g));
  if (index) {
    if (/^disallow:\s*\/\s*$/im.test(text)) problems.push("robots.txt: an indexed site must not Disallow: /");
    for (const bot of AI_CRAWLERS) {
      const g = group(bot);
      if (!g) problems.push(`robots.txt: ${bot} has no group (AI crawlers are welcome on public sites)`);
      else if (!/^allow:\s*\/\s*$/im.test(g)) problems.push(`robots.txt: ${bot} is not allowed`);
    }
  } else {
    if (!/user-agent:\s*\*[\s\S]*?disallow:\s*\/\s*(\n|$)/i.test(text))
      problems.push("robots.txt: a site that is not indexed must Disallow: / for *");
    // A site that is not indexed advertises nothing: no sitemap line (dev, preview, consoles).
    if (/^sitemap:/im.test(text)) problems.push("robots.txt: a site that is not indexed must not advertise a sitemap");
  }
  return problems;
}

/** sitemap.xml: every URL on the host, an hreflang alternate (when any) on the host too.
 * @param {string} xml
 * @param {{ host: string, minUrls?: number }} opts
 * @returns {string[]} */
export function auditSitemap(xml, { host, minUrls = 1 }) {
  const problems = [];
  const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1].trim());
  if (locs.length < minUrls) problems.push(`sitemap.xml: ${locs.length} URL(s), expected at least ${minUrls}`);
  for (const loc of locs)
    if (!loc.startsWith(`${host}/`) && loc !== host) problems.push(`sitemap.xml: ${loc} is not on ${host}`);
  for (const link of tags(xml, "xhtml:link")) {
    const href = attr(link, "href");
    if (href && !href.startsWith(`${host}/`) && href !== host)
      problems.push(`sitemap.xml: alternate ${href} is not on ${host}`);
  }
  if (!/<lastmod>/.test(xml)) problems.push("sitemap.xml: no <lastmod>");
  return problems;
}

/** llms.txt (llmstxt.org): H1, blockquote summary, a link list, the Krizaka family, no foreign host for the site.
 * @param {string} text
 * @param {{ host: string, full?: boolean }} opts - `full`: llms-full.txt, whose documentation may quote `{{ }}` templates
 * @returns {string[]} */
export function auditLlms(text, { host, full = false }) {
  const problems = [];
  if (!/^# \S/.test(text)) problems.push("llms.txt: must start with an H1 (# Name)");
  if (!/^> \S/m.test(text)) problems.push("llms.txt: no blockquote summary (> …)");
  if (!/^- \[[^\]]+\]\(https?:\/\/[^)]+\)/m.test(text) && !/^- .*https?:\/\//m.test(text))
    problems.push("llms.txt: no list of key pages");
  if (!/(^|[\s(<])https:\/\/www\.krizaka\.com(?=[\s/)>#?]|$)/m.test(text))
    problems.push("llms.txt: does not link to https://www.krizaka.com");
  if (!full && text.includes("{{")) problems.push("llms.txt: unfilled placeholder");
  if (/https:\/\/krizaka\.com/.test(text)) problems.push("llms.txt: declares the apex https://krizaka.com (use www)");
  if (host !== "https://www.krizaka.com" && !text.includes(host)) problems.push(`llms.txt: never mentions ${host}`);
  return problems;
}

/** JSON-LD nodes of a page, flattened. */
export function jsonLd(html) {
  const out = [];
  for (const m of html.matchAll(/<script type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)) {
    const data = JSON.parse(m[1]);
    for (const node of data["@graph"] ?? [].concat(data)) out.push(node);
  }
  return out;
}

const refId = (v) => (typeof v === "string" ? v : v?.["@id"]);
const leadsToOrg = (node) => {
  if (node["@id"] === ORG_ID) return true;
  return [
    "publisher",
    "provider",
    "author",
    "creator",
    "isPartOf",
    "parentOrganization",
    "maintainer",
    "copyrightHolder",
    "brand",
  ].some((k) => [].concat(node[k] ?? []).some((v) => refId(v) === ORG_ID));
};

/** One page of the site. `path` is its route ("/", "/fr/"); `host` the public origin.
 * @param {string} html
 * @param {string} path
 * @param {{ host: string, index?: boolean, types?: string[], home?: boolean, organizationHome?: boolean }} opts
 * @returns {string[]} */
export function auditHtml(html, path, { host, index = true, types = [], home = false, organizationHome = false }) {
  const problems = [];
  const titles = [...html.matchAll(/<title>([^<]*)<\/title>/g)].map((m) => decode(m[1]));
  if (titles.length !== 1) problems.push(`${titles.length} <title> (expected 1)`);
  else if (!titles[0].trim()) problems.push("empty <title>");
  const metas = tags(html, "meta");
  const meta = (key, v) => metas.filter((m) => attr(m, key) === v).map((m) => attr(m, "content"));
  const desc = meta("name", "description");
  if (desc.length !== 1) problems.push(`${desc.length} meta description (expected 1)`);
  else if (decode(desc[0]).length < 30 || decode(desc[0]).length > 400)
    problems.push(`description length ${decode(desc[0]).length}`);

  const links = tags(html, "link");
  const canonical = links.filter((l) => /rel="canonical"/.test(l)).map((l) => attr(l, "href"));
  const expected = (u) => strip(u);
  if (canonical.length !== 1) problems.push(`${canonical.length} canonical (expected 1)`);
  else if (!(canonical[0] === `${host}${path}` || expected(canonical[0]) === expected(`${host}${path}`)))
    problems.push(`canonical ${canonical[0]} is not ${host}${path}`);
  else if (!canonical[0].startsWith(host)) problems.push(`canonical ${canonical[0]} is off ${host}`);
  for (const l of links.filter((l) => /hreflang/i.test(l))) {
    const href = attr(l, "href");
    if (!href?.startsWith(host)) problems.push(`hreflang ${attr(l, "hreflang")} off host: ${href}`);
  }
  for (const [what, v] of [
    ["og:title", meta("property", "og:title")],
    ["og:description", meta("property", "og:description")],
    ["og:image", meta("property", "og:image")],
    ["twitter:card", meta("name", "twitter:card")],
  ]) {
    if (v.length === 0 || !v[0]) problems.push(`${what} missing`);
  }
  const og = meta("property", "og:image")[0];
  if (og && !/^https:\/\//.test(og)) problems.push(`og:image is not absolute: ${og}`);
  const robots = (meta("name", "robots")[0] ?? "").toLowerCase();
  if (index && /noindex/.test(robots)) problems.push(`indexed site but robots meta is "${robots}"`);
  if (!index && !/noindex/.test(robots)) problems.push("site is not indexed but the page has no robots noindex meta");

  let nodes = [];
  try {
    nodes = jsonLd(html);
  } catch (e) {
    problems.push(`JSON-LD does not parse: ${e.message}`);
  }
  if (nodes.length === 0) problems.push("no JSON-LD");
  const have = new Set(nodes.flatMap((n) => [].concat(n["@type"] ?? [])));
  if (home) for (const t of types) if (!have.has(t)) problems.push(`home: ${t} JSON-LD missing`);
  if (home && organizationHome && !nodes.some((n) => n["@id"] === ORG_ID))
    problems.push(`home: the Organization node must carry @id ${ORG_ID}`);
  if (!organizationHome) {
    const subjects = nodes.filter(
      (n) => !["BreadcrumbList", "ListItem", "Question", "Answer"].includes([].concat(n["@type"])[0]),
    );
    if (subjects.length && !subjects.some(leadsToOrg))
      problems.push(`JSON-LD never points back to the Krizaka Organization (${ORG_ID})`);
  }
  const declared = [...links, ...metas].join("\n");
  if (/https:\/\/krizaka\.com/.test(declared)) problems.push("the apex https://krizaka.com is declared (use www)");
  return problems;
}

/** An operator console (admin.*): not indexed, said in the page itself.
 * @param {string} html
 * @returns {string[]} */
export function auditConsoleHtml(html) {
  const robots = (
    tags(html, "meta")
      .filter((m) => attr(m, "name") === "robots")
      .map((m) => attr(m, "content"))[0] ?? ""
  ).toLowerCase();
  return /noindex/.test(robots) ? [] : ['the page has no <meta name="robots" content="noindex">'];
}

/** Response headers: X-Robots-Tag must agree with the indexing policy.
 * @param {Headers} headers
 * @param {{ index?: boolean }} opts
 * @returns {string[]} */
export function auditHeaders(headers, { index = true }) {
  const tag = (headers.get?.("x-robots-tag") ?? "").toLowerCase();
  if (index && /noindex/.test(tag)) return [`indexed site but X-Robots-Tag is "${tag}"`];
  if (!index && !/noindex/.test(tag)) return ["site is not indexed but X-Robots-Tag has no noindex"];
  return [];
}

async function main() {
  const args = process.argv.slice(2);
  const opt = (n, d) => (args.includes(n) ? args[args.indexOf(n) + 1] : d);
  const base = strip(opt("--base", "http://localhost:3000"));
  const host = strip(opt("--host", ""));
  if (!host) throw new Error("--host <public origin> is required");
  const index = opt("--index", "on") !== "off";
  const types = (opt("--types", "") || "").split(",").filter(Boolean);
  const limit = Number(opt("--limit", 25));
  const organizationHome = args.includes("--organization");
  let failed = 0;
  const report = (where, problems) => {
    for (const p of problems) process.stderr.write(`${`✗ ${where}: ${p}`}\n`);
    failed += problems.length;
  };
  const get = async (p) => {
    const res = await fetch(`${base}${p}`, { redirect: "follow" });
    if (!res.ok) throw new Error(`${p}: HTTP ${res.status}`);
    return res;
  };
  const consoleMode = args.includes("--console");
  report("/robots.txt", auditRobots(await (await get("/robots.txt")).text(), { host, index, sitemap: !consoleMode }));
  if (consoleMode) {
    const res = await get("/");
    report("/", auditHeaders(res.headers, { index: false }));
    report("/", auditConsoleHtml(await res.text()));
    return done(1);
  }
  if (!args.includes("--no-llms")) report("/llms.txt", auditLlms(await (await get("/llms.txt")).text(), { host }));
  let paths = ["/"];
  if (index && !args.includes("--no-sitemap")) {
    const xml = await (await get("/sitemap.xml")).text();
    report("/sitemap.xml", auditSitemap(xml, { host }));
    paths = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => new URL(m[1]).pathname).slice(0, limit);
  }
  for (const p of paths) {
    const res = await get(p);
    report(p, auditHeaders(res.headers, { index }));
    report(p, auditHtml(await res.text(), p, { host, index, types, home: p === "/", organizationHome }));
  }
  done(paths.length);

  function done(pages) {
    if (failed) {
      process.stderr.write(`SEO family check: ${failed} problem(s) on ${pages} page(s) of ${host}\n`);
      process.exit(1);
    }
    process.stdout.write(
      `SEO family check: ${pages} page(s) and robots.txt of ${host} (${index ? "indexed" : "noindex"}${consoleMode ? ", console" : ""}) — all good\n`,
    );
  }
}

/* Run as a script (not when a test imports the auditors; also loadable by Jest, hence no import.meta). */
if (/check-seo-family\.mjs$/.test(process.argv[1] ?? "")) {
  main().catch((e) => {
    process.stderr.write(`${e}\n`);
    process.exit(1);
  });
}
