/**
 * @file settings.constants.ts
 * @description Immutable domain constants for the Settings feature module.
 *
 * All configuration option sets are defined as `as const` arrays to serve as the
 * single source of truth. Derived union types provide zero-cost TypeScript safety.
 * Exhaustive `Record<K, V>` label maps guarantee compile-time breakage when a new
 * option value is introduced without supplying its corresponding translation key.
 */

// ── Source of Truth (as const arrays) ──────────────────────────────────

export const AI_PERSONAS = ["standard", "concise", "creative"] as const;
export const THEME_ACCENTS = ["rose", "emerald", "amber", "zinc", "indigo", "violet"] as const;
export const THEME_LAYOUTS = ["standard", "compact"] as const;

// ── Derived Pure Types (zero JavaScript footprint) ─────────────────────

export type AiPersona = (typeof AI_PERSONAS)[number];
export type ThemeAccent = (typeof THEME_ACCENTS)[number];
export type ThemeLayout = (typeof THEME_LAYOUTS)[number];

// ── Exhaustive i18n Label Keys (Record<K, V> compile-time guard) ───────

export const AI_PERSONA_LABELS: Record<AiPersona, string> = {
  standard: "settings.standardPersona",
  concise: "settings.concisePersona",
  creative: "settings.creativePersona",
};

export const THEME_ACCENT_LABELS: Record<ThemeAccent, string> = {
  zinc: "settings.zincAccent",
  rose: "settings.roseAccent",
  emerald: "settings.emeraldAccent",
  amber: "settings.amberAccent",
  indigo: "settings.indigoAccent",
  violet: "settings.violetAccent",
};

export const THEME_LAYOUT_LABELS: Record<ThemeLayout, string> = {
  standard: "settings.standardLayout",
  compact: "settings.compactLayout",
};
