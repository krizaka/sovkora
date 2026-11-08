/**
 * Settings object representing user preferences and theme customization.
 *
 * All union types are derived from the canonical `as const` arrays in
 * `settings.constants.ts` to guarantee a single source of truth.
 */

import type { AiPersona, ThemeAccent, ThemeLayout } from "@/constants/settings.constants";
import type { Appearance } from "@/core/hooks/useAppearance";

export interface Settings {
  language: string;
  autoSave: boolean;
  aiPersona: AiPersona;
  themeName: string;
  themeTagline: string;
  themeAccent: ThemeAccent;
  themeLayout: ThemeLayout;
  theme: Appearance;
  tenantId: string;
}
