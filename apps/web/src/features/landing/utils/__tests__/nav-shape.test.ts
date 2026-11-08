/**
 * Dev preview `ux.ia-review` (orazaka-web-client#125): the SHAPE of the navigation is a tested contract, not a habit.
 * docs/ux/information-architecture.md of the Sovkora workspace says why; this guards what it says — about five
 * top-level items, no mixed concepts in one menu, no orphan entry, one list for every surface.
 */
import { APP_SECTIONS, AppSectionPlace, appSectionsFor } from "@/core/config/app-sections";
import { NAV_DOCS, NAV_GROUPS, NAV_PAGES } from "@/features/landing/utils/site-nav";

describe("the signed-in app", () => {
  const sections = appSectionsFor(true);
  const main = sections.filter((s) => s.place === AppSectionPlace.Main);
  const foot = sections.filter((s) => s.place === AppSectionPlace.Foot);

  it("keeps about five places on the rail (six at most) and two at its foot", () => {
    expect(main.length).toBeLessThanOrEqual(6);
    expect(foot.length).toBeLessThanOrEqual(2);
  });

  it("lists every place once, with its own address", () => {
    const ids = sections.map((s) => s.id);
    const hrefs = sections.map((s) => s.href);
    expect(new Set(ids).size).toBe(ids.length);
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });

  it("shows Admin to admins only, and never on the main rail", () => {
    expect(appSectionsFor(false).some((s) => s.id === "admin")).toBe(false);
    expect(APP_SECTIONS.filter((s) => s.adminOnly).every((s) => s.place === AppSectionPlace.Foot)).toBe(true);
  });

  it("puts the jobs of a Krizaka user in this order: do (Studios, Chat), follow (Jobs), manage (Packs, Usage)", () => {
    expect(main.map((s) => s.id)).toEqual(["home", "studios", "chat", "jobs", "packs", "usage"]);
  });
});

describe("the public bar", () => {
  it("has at most four top-level items: the Product menu, Docs, Pricing and Open source", () => {
    expect(NAV_GROUPS.length + 1 + NAV_PAGES.length).toBeLessThanOrEqual(4);
    expect(NAV_PAGES.map((p) => p.path)).toEqual(["/pricing", "/open-source"]);
    expect(NAV_DOCS.path).toBe("/docs");
  });

  it("holds at most four entries per menu, none in two menus", () => {
    for (const group of NAV_GROUPS) expect(group.entries.length).toBeLessThanOrEqual(4);
    const ids = NAV_GROUPS.flatMap((g) => g.entries.map((e) => e.id));
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("leaves no orphan: every entry is an anchor of the home or a page, and Docs never sits in a menu", () => {
    for (const group of NAV_GROUPS) {
      for (const entry of group.entries) expect(Boolean(entry.anchor) !== Boolean(entry.path)).toBe(true);
      expect(group.entries.some((e) => e.path === NAV_DOCS.path)).toBe(false);
    }
  });
});
