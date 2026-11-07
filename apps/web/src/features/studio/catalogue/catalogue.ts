/**
 * @file catalogue.ts
 * @description The Studio list, as pure functions: one row per Studio with its state and where it comes from, the
 * filters of the address (search, state, trade), the trade headings and the counts. No fetch, no React: the screen
 * passes what the services answered.
 */
import {
  LockReasonSchema,
  PackKindSchema,
  StudioPricingSchema,
  type Installation,
  type StudioSummary,
} from "@krizaka/orazaka-shared";
import {
  CatalogueView,
  StudioSource,
  StudioState,
  type CatalogueGroup,
  type CataloguePack,
  type CatalogueQuery,
  type CatalogueRow,
} from "./catalogue.types";

const LOCK = LockReasonSchema.enum;
const KIND = PackKindSchema.enum;
const PRICING = StudioPricingSchema.enum;

/** Lower case, no accents, so « resume » finds « Résumé de séance ». */
export function fold(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .trim();
}

/** The state of one Studio for this person, from the server's verdict. */
export function stateOf(studio: Pick<StudioSummary, "locked" | "lockedReason">): StudioState {
  if (!studio.locked) return StudioState.Ready;
  if (studio.lockedReason === LOCK.REQUIRES_PURCHASE) return StudioState.NeedsPack;
  if (studio.lockedReason === LOCK.REQUIRES_PLAN) return StudioState.NeedsPlan;
  return StudioState.Unknown;
}

function sourceOf(studio: StudioSummary, state: StudioState, pack: CataloguePack | null): CatalogueRow["source"] {
  if (state !== StudioState.Ready) return null;
  if (studio.kind === KIND.TOOLKIT || pack?.access === "INCLUDED") return StudioSource.Plan;
  if (pack?.owned) return StudioSource.Pack;
  if (studio.pricing === PRICING.FREE) return StudioSource.Free;
  if (studio.pricing === PRICING.INCLUDED) return StudioSource.Plan;
  return StudioSource.Granted;
}

/**
 * One row per Studio, in the catalogue's order.
 *
 * @param studios - the catalogue (`/api/v1/studios`), locked ones included
 * @param installations - what this person installed (for « new version »)
 * @param packs - the packs with this person's access and holdings, to name the pack of each Studio
 * @param tradeLabel - the words of a trade, so the search finds « Immobilier »
 */
export function buildRows(
  studios: readonly StudioSummary[],
  installations: readonly Pick<Installation, "studioKey" | "pinnedVersion" | "latestVersion">[],
  packs: readonly CataloguePack[],
  tradeLabel: (trade: string) => string,
): CatalogueRow[] {
  const packOf = new Map<string, CataloguePack>();
  for (const pack of packs) for (const key of pack.studioKeys) packOf.set(key, pack);
  const installed = new Map(installations.map((i) => [i.studioKey, i]));

  return studios.map((studio) => {
    const state = stateOf(studio);
    const pack =
      packOf.get(studio.studioKey) ??
      (studio.packKey ? (packs.find((p) => p.packKey === studio.packKey) ?? null) : null);
    const installation = installed.get(studio.studioKey);
    const latest = studio.latestVersion ?? installation?.latestVersion ?? null;
    return {
      studio,
      state,
      source: sourceOf(studio, state, pack),
      pack,
      hasUpdate: Boolean(installation && latest && installation.pinnedVersion !== latest),
      haystack: fold([studio.label, studio.tagline ?? "", tradeLabel(studio.profession), pack?.label ?? ""].join(" ")),
    };
  });
}

function inView(row: CatalogueRow, view: CatalogueQuery["view"]): boolean {
  if (view === CatalogueView.Ready) return row.state === StudioState.Ready;
  if (view === CatalogueView.More) return row.state !== StudioState.Ready;
  return true;
}

/** The rows the address asks for: every word of the search, the state, the trade. */
export function filterRows(rows: readonly CatalogueRow[], { query, view, trade }: CatalogueQuery): CatalogueRow[] {
  const words = fold(query).split(/\s+/).filter(Boolean);
  return rows.filter(
    (row) =>
      inView(row, view) &&
      (trade === null || row.studio.profession === trade) &&
      words.every((word) => row.haystack.includes(word)),
  );
}

/** How many rows each view would show, for the labels of the state filter (search and trade applied). */
export function countViews(
  rows: readonly CatalogueRow[],
  query: Omit<CatalogueQuery, "view">,
): Record<CatalogueView, number> {
  const scoped = filterRows(rows, { ...query, view: CatalogueView.All });
  const ready = scoped.filter((row) => row.state === StudioState.Ready).length;
  return {
    [CatalogueView.All]: scoped.length,
    [CatalogueView.Ready]: ready,
    [CatalogueView.More]: scoped.length - ready,
  };
}

/**
 * Trade headings, in the order of their words, « General » last (it is what is left once the trades are read).
 * Inside a heading, ready Studios first, then the catalogue's order.
 */
export function groupByTrade(
  rows: readonly CatalogueRow[],
  tradeLabel: (trade: string) => string,
  generalKey = "general",
): CatalogueGroup[] {
  const groups = new Map<string, CatalogueRow[]>();
  for (const row of rows) {
    const list = groups.get(row.studio.profession) ?? [];
    list.push(row);
    groups.set(row.studio.profession, list);
  }
  const rank = (state: CatalogueRow["state"]) => (state === StudioState.Ready ? 0 : 1);
  return [...groups.entries()]
    .map(([trade, list]) => ({
      trade,
      label: tradeLabel(trade),
      rows: list
        .map((row, index) => ({ row, index }))
        .sort((a, b) => rank(a.row.state) - rank(b.row.state) || a.index - b.index)
        .map(({ row }) => row),
    }))
    .sort((a, b) => Number(a.trade === generalKey) - Number(b.trade === generalKey) || a.label.localeCompare(b.label));
}

/** The trades present in the catalogue, for the trade filter, in the order of their words. */
export function tradesOf(
  rows: readonly CatalogueRow[],
  tradeLabel: (trade: string) => string,
): { key: string; label: string }[] {
  return [...new Set(rows.map((row) => row.studio.profession))]
    .map((key) => ({ key, label: tradeLabel(key) }))
    .sort((a, b) => a.label.localeCompare(b.label));
}

/** Reads the filters from the address; anything unknown falls back to « everything ». */
export function readQuery(params: { get(name: string): string | null }): CatalogueQuery {
  const show = params.get("show");
  const view = Object.values(CatalogueView).find((v) => v === show) ?? CatalogueView.All;
  return { query: params.get("q") ?? "", view, trade: params.get("trade") || null };
}

/** Writes the filters back, leaving out what is the default, so a clean list is a clean address. */
export function writeQuery(current: URLSearchParams, next: Partial<CatalogueQuery>): string {
  const params = new URLSearchParams(current.toString());
  const set = (name: string, value: string | null | undefined, empty: string | null = "") => {
    if (value === undefined) return;
    if (value === null || value === empty) params.delete(name);
    else params.set(name, value);
  };
  set("q", next.query?.trim() === "" ? null : next.query);
  set("show", next.view, CatalogueView.All);
  set("trade", next.trade);
  const text = params.toString();
  return text ? `?${text}` : "";
}
