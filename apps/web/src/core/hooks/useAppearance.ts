"use client";

import * as React from "react";
import { useTheme } from "@krizaka/ui/theme";

/**
 * @file useAppearance.ts
 * @description The one appearance choice of the person — a mode or a named theme — on the organisation's mechanism
 * (@krizaka/ui/theme): dark by default, `html.light` for light, `html.theme-<name>` for a named theme.
 *
 * The profile stores a single value (`preferences.theme`): "system" | "light" | "dark" or a named theme. A named
 * theme sets every colour role, so its mode only decides `color-scheme`: solarized is light, the others dark.
 * `electric` is the 1.x blue identity, kept for the people who prefer it (the default is the Sovkora orange).
 */

/** The named themes of @krizaka/orazaka-design-system (theme.css). */
export const NAMED_THEMES = ["electric", "custom", "cyberpunk", "solarized", "krizaka"] as const;
export type NamedTheme = (typeof NAMED_THEMES)[number];

export const APPEARANCES = ["system", "light", "dark", ...NAMED_THEMES] as const;
export type Appearance = (typeof APPEARANCES)[number];

const NAMED_MODE: Record<NamedTheme, "dark" | "light"> = {
  electric: "dark",
  custom: "dark",
  cyberpunk: "dark",
  solarized: "light",
  krizaka: "dark",
};

const isNamed = (value: string): value is NamedTheme => (NAMED_THEMES as readonly string[]).includes(value);

/** A stored value (profile, settings) → an appearance; anything unknown reads as "system". */
export function toAppearance(value: string | null | undefined): Appearance {
  return (APPEARANCES as readonly string[]).includes(value ?? "") ? (value as Appearance) : "system";
}

export interface AppearanceValue {
  appearance: Appearance;
  setAppearance: (appearance: Appearance) => void;
}

/** Reads and applies the appearance. Needs the @krizaka/ui ThemeProvider above it. */
export function useAppearance(): AppearanceValue {
  const { mode, setMode, theme, setTheme } = useTheme();
  const appearance: Appearance = theme && isNamed(theme) ? theme : mode;

  const setAppearance = React.useCallback(
    (next: Appearance) => {
      if (isNamed(next)) {
        setTheme(next);
        setMode(NAMED_MODE[next]);
      } else {
        setTheme(null);
        setMode(next);
      }
    },
    [setMode, setTheme],
  );

  return { appearance, setAppearance };
}
