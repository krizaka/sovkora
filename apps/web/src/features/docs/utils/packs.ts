/**
 * @file packs.ts
 * @description The `pack-index` and `pack` fenced blocks of /docs/packs — data written by Sovkora's docs generator
 * (scripts/lib/pack-blocks.mjs) from orazaka-packs. A block that is not the shape the cards need fails the build.
 */

export interface PackStudio {
  key: string;
  label: string;
  summary: string | null;
  description: string | null;
  inputs: { key: string; title: string | null }[];
  steps: number;
  stepKinds: string[];
  credits: number | null;
}

export interface PackRuleset {
  label: string;
  jurisdiction: string | null;
  rules: number;
  versions: string[];
}

export interface PackData {
  key: string;
  title: string;
  tagline: string | null;
  description: string | null;
  version: string | null;
  tier: string | null;
  kind: string | null;
  distribution: string | null;
  regulatoryClass: string | null;
  category: string | null;
  listed: boolean;
  workers: string[];
  controls: { scopeGuard: boolean; consent: boolean; safety: boolean };
  rulesets: PackRuleset[];
  studios: PackStudio[];
}

export interface PackIndexItem {
  key: string;
  title: string;
  tagline: string | null;
  tier: string | null;
  category: string | null;
  studios: number;
}

function json(source: string, what: string): unknown {
  try {
    return JSON.parse(source);
  } catch {
    throw new Error(`${what}: the block is not valid JSON`);
  }
}

export function parsePack(source: string): PackData {
  const p = json(source, "pack") as Partial<PackData>;
  if (
    !p ||
    typeof p.key !== "string" ||
    typeof p.title !== "string" ||
    !Array.isArray(p.studios) ||
    !Array.isArray(p.rulesets)
  ) {
    throw new Error("pack: expected { key, title, studios[], rulesets[] }");
  }
  for (const s of p.studios)
    if (typeof s.key !== "string" || typeof s.label !== "string" || !Array.isArray(s.inputs))
      throw new Error(`pack ${p.key}: a Studio lacks key, label or inputs`);
  return p as PackData;
}

export function parsePackIndex(source: string): PackIndexItem[] {
  const items = json(source, "pack-index");
  if (!Array.isArray(items) || items.some((i) => typeof i?.key !== "string" || typeof i?.title !== "string"))
    throw new Error("pack-index: expected [{ key, title, … }]");
  return items as PackIndexItem[];
}

/** The slug the markdown renderer gives a pack's `###` heading — where the index cards lead. */
export const packAnchor = (title: string): string => {
  const slug = title.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const start = slug.startsWith("-") ? 1 : 0;
  const end = slug.length > start && slug.endsWith("-") ? slug.length - 1 : slug.length;
  return slug.slice(start, end);
};

/** Filters the index by tier and category (null = all). */
export function filterPacks(
  items: readonly PackIndexItem[],
  tier: string | null,
  category: string | null,
): PackIndexItem[] {
  return items.filter((i) => (!tier || i.tier === tier) && (!category || i.category === category));
}
