/**
 * Epic krizaka/orazaka-web-client#231 — the public site is small: every item of the bar and the footer resolves, every
 * removed address answers 308 to a page that exists, and the sitemap lists only what is there.
 */
import fs from "node:fs";
import path from "node:path";
import { PUBLIC_REDIRECTS, REMOVED_PUBLIC, SIGNED_OUT_REDIRECTS } from "@/core/config/redirects";
import { NAV_DOCS, NAV_GROUPS, NAV_PAGES } from "@/features/landing/utils/site-nav";
import { findPage } from "@/features/docs/utils/docs";
import { publicPaths, llmsText } from "@/app/_seo/seo";
import { JOURNEY_IDS } from "@/core/constants/docs-journeys";

const root = process.cwd();
const APP = path.join(root, "src", "app");
const isPage = (p: string) => fs.existsSync(path.join(APP, p, "page.tsx"));

/** A path resolves when a page file serves it, or it is a published doc. */
function resolves(p: string): boolean {
  if (p === "/") return isPage("") || fs.existsSync(path.join(APP, "page.tsx"));
  if (p.startsWith("/docs/")) return Boolean(findPage(p.slice("/docs/".length))) || isPage(p);
  return isPage(p);
}

/** The ids the landing components declare (`id="records"`, `<Section id="features"`), what an `#anchor` must reach. */
function homeIds(): Set<string> {
  const dir = path.join(root, "src", "features", "landing", "components");
  // `features` is the id @krizaka/ui's landing template gives its features section (LandingHome.test checks it renders).
  const ids = new Set<string>(["features"]);
  for (const f of fs.readdirSync(dir).filter((n) => n.endsWith(".tsx"))) {
    for (const m of fs.readFileSync(path.join(dir, f), "utf8").matchAll(/\bid[:=]\s*\{?["']([a-z-]+)["']/g))
      ids.add(m[1]);
  }
  return ids;
}

describe("the bar", () => {
  const entries = NAV_GROUPS.flatMap((g) => g.entries);

  it("is short: one menu of at most three entries, and Docs", () => {
    expect(NAV_GROUPS).toHaveLength(1);
    expect(entries.length).toBeLessThanOrEqual(3);
  });

  it.each(entries.map((e) => [e.id, e] as const))("entry %s resolves", (_id, e) => {
    if (e.path) expect(resolves(e.path)).toBe(true);
    else expect(homeIds().has(e.anchor!)).toBe(true);
  });

  it("has no Packs: it is featured in the docs, not in the bar or the footer", () => {
    expect(entries.map((e) => e.id)).toEqual(["how", "download"]);
    expect(entries.some((e) => e.path?.includes("packs"))).toBe(false);
  });

  it("Docs resolves", () => expect(isPage(NAV_DOCS.path)).toBe(true));

  it.each(NAV_PAGES.map((p) => [p.id, p.path] as const))("%s (%s) resolves and is in the sitemap", (_id, p) => {
    expect(isPage(p)).toBe(true);
    expect(publicPaths()).toContain(p);
  });
});

describe("the lean public site (#290)", () => {
  it("/docs/licensing answers 308 to /open-source, and the licence is no longer a published doc", () => {
    expect(PUBLIC_REDIRECTS.find((r) => r.source === "/docs/licensing")).toMatchObject({
      destination: "/open-source",
      permanent: true,
    });
    expect(findPage("licensing")).toBeNull();
    expect(publicPaths()).not.toContain("/docs/licensing");
  });

  it("/contact answers 308 to krizaka.com/contact?topic=sovkora&source=sovkora-app", () => {
    expect(PUBLIC_REDIRECTS.find((r) => r.source === "/contact")).toMatchObject({
      destination: "https://www.krizaka.com/en/contact?topic=sovkora&source=sovkora-app",
      permanent: true,
    });
    expect(PUBLIC_REDIRECTS.find((r) => r.source === "/fr/contact")).toMatchObject({
      destination: "https://www.krizaka.com/fr/contact?topic=sovkora&source=sovkora-app",
    });
  });
});

describe("removed public addresses", () => {
  it.each(REMOVED_PUBLIC.map((r) => [r.source, r.destination] as const))(
    "%s answers 308 to %s, which exists",
    (source, destination) => {
      const rule = PUBLIC_REDIRECTS.find((r) => r.source === source);
      expect(rule?.permanent).toBe(true);
      const [target, hash] = destination.split("#");
      expect(resolves(target || "/")).toBe(true);
      if (hash) expect(homeIds().has(hash)).toBe(true);
      // The page itself is gone: a redirect to a page that still exists would hide a forgotten file.
      expect(isPage(source.replace(/\/:id$/, ""))).toBe(false);
    },
  );

  it("never redirects to another removed address", () => {
    const removed = new Set(REMOVED_PUBLIC.map((r) => r.source));
    for (const r of PUBLIC_REDIRECTS) expect(removed.has(r.destination.split("#")[0])).toBe(false);
  });
});

describe("Packs, now in the docs", () => {
  it("/fr/packs and /en/packs answer 308 to /docs/packs, a published page", () => {
    for (const source of ["/fr/packs", "/en/packs"])
      expect(PUBLIC_REDIRECTS.find((r) => r.source === source)).toMatchObject({
        destination: "/docs/packs",
        permanent: true,
      });
    expect(resolves("/docs/packs")).toBe(true);
  });

  it("/packs is the signed-in Plan & packs: only a visitor without a session is sent to the docs (308, by the proxy)", () => {
    expect(SIGNED_OUT_REDIRECTS).toEqual([{ source: "/packs", destination: "/docs/packs" }]);
    expect(isPage("(protected)/packs")).toBe(true);
  });

  it("is a docs section of its own, and in the sitemap and llms.txt", () => {
    expect(JOURNEY_IDS).toContain("packs");
    expect(publicPaths()).toContain("/docs/packs");
    expect(llmsText()).toContain("/docs/packs");
  });
});

describe("the sitemap", () => {
  it("lists only pages that exist, none removed", () => {
    for (const p of publicPaths()) expect(resolves(p)).toBe(true);
    const removed = REMOVED_PUBLIC.map((r) => r.source.replace(/\/:id$/, ""));
    for (const p of publicPaths()) expect(removed.some((r) => p === r || p.startsWith(`${r}/`))).toBe(false);
  });
});
