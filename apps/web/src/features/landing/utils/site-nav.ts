/**
 * The public bar of www.orazaka.com as data — the product-site standard of every Krizaka product (addendum 2026-10-15,
 * docs/ux/information-architecture.md of the Sovkora workspace): the mark, then at most four entries grouped by the
 * visitor's question — **Product ▾** (what is it, what does it install, where do I get it?), **Docs** (how do I use
 * it?), then **Pricing** and **Open source** as direct links (#290). Simplified for epic #231: the gallery, the models page, the feature tours and the
 * platforms menu are gone (docs and the home hold what they said).
 * The bar, the mobile panel and the footer read this one list, in this order.
 */
import type { ComponentType } from "react";
import { LocalIcon, PlayIcon, type IconProps } from "@krizaka/icons";

/** The words of an entry live in `t.landing.nav[id]` and its line in `t.landing.nav.menu[id]`. */
export const NavEntryId = {
  How: "how",
  Download: "download",
} as const;
export type NavEntryId = (typeof NavEntryId)[keyof typeof NavEntryId];

export const NavGroupId = { Product: "product" } as const;
export type NavGroupId = (typeof NavGroupId)[keyof typeof NavGroupId];

export interface NavEntry {
  id: NavEntryId;
  /** A section of the home page (`#id` there, `/#id` elsewhere) — or a page. */
  anchor?: string;
  path?: string;
  Icon: ComponentType<IconProps>;
  /** The pages this entry is the current one for (exact path, or the path and everything under it). */
  current?: readonly string[];
}

export interface NavGroup {
  id: NavGroupId;
  entries: readonly NavEntry[];
}

/** Product ▾ — what Sovkora does (the home's « What it does »), and where to get it (the Mac app). */
const PRODUCT: NavGroup = {
  id: NavGroupId.Product,
  entries: [
    { id: NavEntryId.How, anchor: "features", Icon: PlayIcon },
    { id: NavEntryId.Download, path: "/desktop", Icon: LocalIcon, current: ["/desktop"] },
  ],
};

export const NAV_GROUPS: readonly NavGroup[] = [PRODUCT];

/** Docs — a direct link: the documentation is a reading place, not a menu. */
export const NAV_DOCS = { path: "/docs" } as const;

/** Pricing and Open source — direct links after Docs (the words are `t.landing.nav[id]`). */
export const NAV_PAGES = [
  { id: "pricing", path: "/pricing" },
  { id: "openSource", path: "/open-source" },
] as const;
export type NavPageId = (typeof NAV_PAGES)[number]["id"];

const under = (pathname: string, base: string) => pathname === base || pathname.startsWith(`${base}/`);

/** The entry the page belongs to, if a group holds it. */
export function currentEntry(pathname: string | null | undefined): { group: NavGroupId; entry: NavEntryId } | null {
  const p = pathname ?? "";
  for (const group of NAV_GROUPS) {
    const entry = group.entries.find((e) => e.current?.some((base) => under(p, base)));
    if (entry) return { group: group.id, entry: entry.id };
  }
  return null;
}

/**
 * Where the visitor is, as the bar shows it — at most one top-level item lit: the group holding the page, else Docs for
 * the rest of /docs, else nothing (the home, whose sections are anchors).
 */
export function navActive(pathname: string | null | undefined): {
  top: NavGroupId | "docs" | NavPageId | null;
  entry: NavEntryId | null;
} {
  const hit = currentEntry(pathname);
  if (hit) return { top: hit.group, entry: hit.entry };
  if (under(pathname ?? "", NAV_DOCS.path)) return { top: "docs", entry: null };
  const page = NAV_PAGES.find((n) => under(pathname ?? "", n.path));
  if (page) return { top: page.id, entry: null };
  return { top: null, entry: null };
}

/** The address of an entry: an anchor of the home (`#id` on it, `/#id` elsewhere), or its page. */
export function entryHref(entry: NavEntry, onHome: boolean): string {
  if (entry.anchor) return onHome ? `#${entry.anchor}` : `/#${entry.anchor}`;
  return entry.path ?? "/";
}
