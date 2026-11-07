/**
 * @file settings.api.ts
 * @description Stateless outbound adapter for user preference settings.
 * Extracts network logic previously inlined inside `useSettings.ts`.
 */

import { restRequest } from "./rest-client";
import type { Settings } from "@/core/types/settings.types";

// ── Types ────────────────────────────────────────────────────────────────────

interface ProfilePreferences {
  preferences: Record<string, unknown>;
}

/**
 * Stateless adapter exposing user settings network operations.
 */
export const SettingsApi = {
  /**
   * Fetches user preference configurations from the BFF REST proxy
   * (`GET /api/v1/profile`).
   *
   * @returns The fully-hydrated settings object with defaults applied.
   */
  fetch: async (): Promise<Settings> => {
    const data = await restRequest<ProfilePreferences>("/api/v1/profile");
    const preferences = data?.preferences || {};
    return {
      language: (preferences.language as string) || "en",
      autoSave: (preferences.autoSave as boolean) ?? true,
      aiPersona: (preferences.aiPersona as Settings["aiPersona"]) || "standard",
      themeName: (preferences.themeName as string) || "Sovkora",
      themeTagline: (preferences.themeTagline as string) || "Decoupled Intelligence",
      themeAccent: (preferences.themeAccent as Settings["themeAccent"]) || "zinc",
      themeLayout: (preferences.themeLayout as Settings["themeLayout"]) || "standard",
      theme: (preferences.theme as Settings["theme"]) || "system",
      tenantId: (preferences.tenantId as string) || "orazaka-default",
    };
  },

  /**
   * Submits partial user preference updates to the BFF REST proxy
   * (`PUT /api/v1/profile/preferences`).
   *
   * @param settings - The partial preference keys to modify.
   * @returns The updated preferences payload from the server.
   */
  update: async (settings: Partial<Settings>): Promise<Partial<Settings>> => {
    const data = await restRequest<ProfilePreferences>("/api/v1/profile/preferences", {
      method: "PUT",
      body: settings,
    });
    return (data?.preferences as Partial<Settings>) ?? {};
  },
} as const;
