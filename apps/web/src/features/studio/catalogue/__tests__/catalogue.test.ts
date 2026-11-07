import type { StudioSummary } from "@krizaka/orazaka-shared";
import {
  buildRows,
  countViews,
  filterRows,
  fold,
  groupByTrade,
  readQuery,
  stateOf,
  tradesOf,
  writeQuery,
} from "@/features/studio/catalogue/catalogue";
import {
  CatalogueView,
  StudioSource,
  StudioState,
  type CataloguePack,
} from "@/features/studio/catalogue/catalogue.types";

const studio = (key: string, over: Partial<StudioSummary> = {}): StudioSummary => ({
  studioKey: key,
  label: key,
  tagline: null,
  profession: "general",
  iconKey: "studio",
  heroAssetId: null,
  pricing: "INCLUDED",
  kind: "TOOLKIT",
  latestVersion: "1.0.0",
  locked: false,
  lockedReason: "NONE",
  packKey: null,
  ...over,
});
const pack = (packKey: string, studioKeys: string[], over: Partial<CataloguePack> = {}): CataloguePack => ({
  packKey,
  label: packKey,
  access: "BUYABLE",
  owned: false,
  priceCents: 4900,
  studioKeys,
  ...over,
});
const words: Record<string, string> = {
  general: "General",
  creative: "Creative",
  "real-estate": "Real estate",
  legal: "Legal",
};
const label = (trade: string) => words[trade] ?? trade;

// The catalogue the QA account sees on dev (20 Oct 2026), cut to one Studio of each case.
const catalogue = [
  studio("image-generation", { label: "Image generation" }),
  studio("realestate-reels", {
    label: "Real-estate reels",
    profession: "real-estate",
    pricing: "PAID",
    kind: "VERTICAL",
  }),
  studio("session-summary", { label: "Résumé de séance", pricing: "FREE", kind: "VERTICAL" }),
  studio("creator-suggestions", {
    label: "Creator suggestions",
    profession: "creative",
    pricing: "PAID",
    kind: "VERTICAL",
    locked: true,
    lockedReason: "REQUIRES_PURCHASE",
    packKey: "data-analytics",
  }),
  studio("media-safety", {
    label: "Media safety",
    pricing: "FREE",
    kind: "VERTICAL",
    locked: true,
    lockedReason: "REQUIRES_PLAN",
  }),
  studio("document-authenticity", {
    label: "Document authenticity",
    profession: "legal",
    pricing: "PAID",
    kind: "VERTICAL",
    locked: true,
    lockedReason: "UNKNOWN",
  }),
  studio("art-golden-hour", { label: "Golden hour", profession: "creative" }),
];
const packs = [
  pack("orazaka-media", ["image-generation", "art-golden-hour"], { access: "INCLUDED", priceCents: 0 }),
  pack("realestate-studio", ["realestate-reels"], { owned: true }),
  pack("data-analytics", ["creator-suggestions"], { label: "Data & analytics" }),
];
const rows = buildRows(
  catalogue,
  [{ studioKey: "realestate-reels", pinnedVersion: "0.9.0", latestVersion: "1.0.0" }],
  packs,
  label,
);
const row = (key: string) => rows.find((r) => r.studio.studioKey === key)!;

describe("the Studio list", () => {
  it("reads the state from the server's verdict, never from the pricing", () => {
    expect(stateOf({ locked: false, lockedReason: "NONE" })).toBe(StudioState.Ready);
    expect(row("creator-suggestions").state).toBe(StudioState.NeedsPack);
    expect(row("media-safety").state).toBe(StudioState.NeedsPlan);
    expect(row("document-authenticity").state).toBe(StudioState.Unknown);
  });

  it("says where a ready Studio comes from: the plan, a pack held, or free", () => {
    expect(row("image-generation").source).toBe(StudioSource.Plan);
    expect(row("realestate-reels").source).toBe(StudioSource.Pack);
    expect(row("session-summary").source).toBe(StudioSource.Free);
    expect(row("creator-suggestions").source).toBeNull();
  });

  it("names the pack that opens a locked Studio", () => {
    expect(row("creator-suggestions").pack?.label).toBe("Data & analytics");
  });

  it("flags an installation pinned behind the newest version", () => {
    expect(row("realestate-reels").hasUpdate).toBe(true);
    expect(row("image-generation").hasUpdate).toBe(false);
  });

  it("finds a Studio by any word of its name, trade or pack, without case or accents", () => {
    expect(fold("Résumé")).toBe("resume");
    const find = (query: string) =>
      filterRows(rows, { query, view: CatalogueView.All, trade: null }).map((r) => r.studio.studioKey);
    expect(find("resume")).toEqual(["session-summary"]);
    expect(find("REAL estate")).toEqual(["realestate-reels"]);
    expect(find("analytics")).toEqual(["creator-suggestions"]);
    expect(find("nothing like this")).toEqual([]);
  });

  it("splits ready from what else exists, and counts each view", () => {
    const keys = (view: CatalogueView) =>
      filterRows(rows, { query: "", view, trade: null }).map((r) => r.studio.studioKey);
    expect(keys(CatalogueView.Ready)).toEqual([
      "image-generation",
      "realestate-reels",
      "session-summary",
      "art-golden-hour",
    ]);
    expect(keys(CatalogueView.More)).toEqual(["creator-suggestions", "media-safety", "document-authenticity"]);
    expect(countViews(rows, { query: "", trade: null })).toEqual({ all: 7, ready: 4, more: 3 });
    expect(countViews(rows, { query: "", trade: "creative" })).toEqual({ all: 2, ready: 1, more: 1 });
  });

  it("groups by trade under their words, General last, ready Studios first", () => {
    const groups = groupByTrade(rows, label);
    expect(groups.map((g) => g.label)).toEqual(["Creative", "Legal", "Real estate", "General"]);
    expect(groups[0].rows.map((r) => r.studio.studioKey)).toEqual(["art-golden-hour", "creator-suggestions"]);
    expect(tradesOf(rows, label).map((t) => t.label)).toEqual(["Creative", "General", "Legal", "Real estate"]);
  });

  it("keeps the filters in the address, and only what differs from the default", () => {
    expect(readQuery(new URLSearchParams("q=reel&show=more&trade=legal"))).toEqual({
      query: "reel",
      view: "more",
      trade: "legal",
    });
    expect(readQuery(new URLSearchParams("show=bogus"))).toEqual({ query: "", view: "all", trade: null });
    expect(writeQuery(new URLSearchParams("q=a"), { query: "", view: CatalogueView.All, trade: null })).toBe("");
    expect(writeQuery(new URLSearchParams(""), { view: CatalogueView.Ready })).toBe("?show=ready");
    expect(writeQuery(new URLSearchParams("show=ready&pack=x"), { trade: "creative" })).toBe(
      "?show=ready&pack=x&trade=creative",
    );
  });
});
