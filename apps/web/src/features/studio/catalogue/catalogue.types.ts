/**
 * @file catalogue.types.ts
 * @description The words of the Studio list as data: what a person can do with one Studio, which list they asked
 * for, and the pack facts a row needs. Named values are `as const` objects (no TS `enum`).
 */
import type { StudioSummary } from "@krizaka/orazaka-shared";

/**
 * What this person can do with one Studio, decided from the server's verdict (`locked`, `lockedReason`).
 *
 * There is no « to install » state: a vertical Studio is set up by its first run (or by one short form when it has
 * settings, on its own page), so for the list it is ready.
 */
export const StudioState = {
  /** Runs now: included in the plan, from a pack held, free, or granted. */
  Ready: "ready",
  /** A pack would open it: the row offers that pack. */
  NeedsPack: "needs-pack",
  /** Another plan would open it. */
  NeedsPlan: "needs-plan",
  /** Billing did not answer: never shown as a refusal, never offered for sale. */
  Unknown: "unknown",
} as const;
export type StudioState = (typeof StudioState)[keyof typeof StudioState];

/** Which rows the person asked for (`?show=` in the address). */
export const CatalogueView = {
  All: "all",
  Ready: "ready",
  /** Everything not ready yet: what else exists and how to get it. */
  More: "more",
} as const;
export type CatalogueView = (typeof CatalogueView)[keyof typeof CatalogueView];

/** Where a ready Studio comes from, said in its row. */
export const StudioSource = {
  Plan: "plan",
  Pack: "pack",
  Free: "free",
  Granted: "granted",
} as const;
export type StudioSource = (typeof StudioSource)[keyof typeof StudioSource];

/** The pack facts a row and the drawer need, passed in by the screen that holds the packs (billing). */
export interface CataloguePack {
  packKey: string;
  label: string;
  /** The server's verdict for this person (ADR-066): INCLUDED · BUYABLE · UNAVAILABLE. */
  access: "INCLUDED" | "BUYABLE" | "UNAVAILABLE";
  /** Whether this person holds it (billing's holdings). */
  owned: boolean;
  priceCents: number | null;
  studioKeys: readonly string[];
}

/** One line of the list. */
export interface CatalogueRow {
  studio: StudioSummary;
  state: StudioState;
  source: StudioSource | null;
  /** The pack the Studio belongs to, when the catalogue knows it. */
  pack: CataloguePack | null;
  /** An installation pinned to an older version than the newest published. */
  hasUpdate: boolean;
  /** Text the search reads: name, line, trade and pack, folded (case and accents). */
  haystack: string;
}

/** The filters, as the address carries them. */
export interface CatalogueQuery {
  query: string;
  view: CatalogueView;
  /** A trade key, or null for every trade. */
  trade: string | null;
}

/** A trade heading and its rows. */
export interface CatalogueGroup {
  trade: string;
  label: string;
  rows: CatalogueRow[];
}
