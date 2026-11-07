import type { RuntimeSetting, SettingValue } from "@/console/services/runtime-config.admin.api";
import { fill, type EngineWords } from "@/console/core/copy/engine";

export const ENGINE_GROUPS = ["run", "webhook", "retention", "marketplace", "downloads", "other"] as const;
export type EngineGroup = (typeof ENGINE_GROUPS)[number];

/** The group of a key, by its prefix (`run.*`, `webhook.*`, `retention.*`, `marketplace.*`, `downloads.*`); `routing.*` is not this screen's. */
export function groupOf(key: string): EngineGroup {
  const prefix = key.split(".")[0];
  return (ENGINE_GROUPS as readonly string[]).includes(prefix) && prefix !== "other"
    ? (prefix as EngineGroup)
    : "other";
}

/** The settings by group, in the group order, each group sorted by key; empty groups are left out. */
export function groupSettings(
  settings: readonly RuntimeSetting[],
): { group: EngineGroup; settings: RuntimeSetting[] }[] {
  return ENGINE_GROUPS.map((group) => ({
    group,
    settings: settings.filter((s) => groupOf(s.key) === group).sort((a, b) => a.key.localeCompare(b.key)),
  })).filter((g) => g.settings.length > 0);
}

/** A value as the editor shows it: the number, `true`/`false`, or one item per line. */
export function draftOf(value: SettingValue): string {
  return Array.isArray(value) ? value.join("\n") : String(value);
}

/** A value as a read-only line. */
export function display(value: SettingValue | null | undefined, words: EngineWords): string {
  if (value === null || value === undefined) return words.empty;
  if (typeof value === "boolean") return value ? words.on : words.off;
  if (Array.isArray(value)) return value.length === 0 ? words.empty : value.join(", ");
  return String(value);
}

export type Parsed = { ok: true; value: SettingValue } | { ok: false; error: string };

/** An integer draft: a whole number inside the bounds, or the words of what is wrong. */
function parseInteger(setting: RuntimeSetting, draft: string, words: EngineWords): Parsed {
  const { minimum, maximum } = setting.bounds;
  const text = draft.trim();
  if (!/^-?\d+$/.test(text)) return { ok: false, error: words.invalid.type };
  const n = Number(text);
  const low = minimum ?? undefined;
  const high = maximum ?? undefined;
  const below = low !== undefined && n < low;
  const above = high !== undefined && n > high;
  if ((below || above) && low !== undefined && high !== undefined)
    return { ok: false, error: fill(words.invalid.range, { min: low, max: high }) };
  if (below) return { ok: false, error: fill(words.invalid.min, { n: low }) };
  if (above) return { ok: false, error: fill(words.invalid.max, { n: high }) };
  return { ok: true, value: n };
}

/** A list draft, one item a line: no empty item, no duplicate, no more than the bound. */
function parseList(setting: RuntimeSetting, draft: string, words: EngineWords): Parsed {
  const { maxItems } = setting.bounds;
  const items = draft.split("\n").map((i) => i.trim());
  const filled = items.length === 1 && items[0] === "" ? [] : items;
  if (filled.includes("")) return { ok: false, error: words.invalid.empty };
  if (new Set(filled).size !== filled.length) return { ok: false, error: words.invalid.duplicate };
  if (maxItems != null && filled.length > maxItems)
    return { ok: false, error: fill(words.invalid.items, { n: maxItems }) };
  return { ok: true, value: filled };
}

/** What was typed, as the typed value the API takes — or the words of what is wrong (the server checks again). */
export function parseDraft(setting: RuntimeSetting, draft: string, words: EngineWords): Parsed {
  if (setting.type === "boolean") return { ok: true, value: draft === "true" };
  return setting.type === "int" ? parseInteger(setting, draft, words) : parseList(setting, draft, words);
}

/** The bounds of a setting as short phrases, for the line under its description. */
export function boundsText(setting: RuntimeSetting, words: EngineWords): string {
  const { minimum, maximum, maxItems } = setting.bounds;
  return [
    minimum != null && setting.type === "int" ? fill(words.min, { n: minimum }) : "",
    maximum != null && setting.type === "int" ? fill(words.max, { n: maximum }) : "",
    maxItems != null && setting.type === "list" ? fill(words.maxItems, { n: maxItems }) : "",
  ]
    .filter(Boolean)
    .join(" · ");
}

/** A setting with its value replaced (the server's answer to a save). */
export const withSetting = (settings: readonly RuntimeSetting[], saved: RuntimeSetting): RuntimeSetting[] =>
  settings.map((s) => (s.key === saved.key ? saved : s));
