"use client";

import * as React from "react";
import { useQuery } from "@krizaka/query";
import { getSession } from "next-auth/react";
import { useSettings } from "@/core/hooks/useSettings";
import { type Appearance, toAppearance, useAppearance } from "@/core/hooks/useAppearance";
import { useTranslation, type Locale } from "@/core/context/LocaleContext";
import {
  THEME_ACCENTS,
  THEME_ACCENT_LABELS,
  type AiPersona,
  type ThemeAccent,
  type ThemeLayout,
} from "@/constants/settings.constants";

/** Editable workspace/appearance preferences, mirrored from `Settings`. */
export interface ProfileFormState {
  language: Locale;
  aiPersona: AiPersona;
  themeName: string;
  themeTagline: string;
  themeAccent: ThemeAccent;
  themeLayout: ThemeLayout;
  theme: Appearance;
  tenantId: string;
}

const EMPTY: ProfileFormState = {
  language: "en",
  aiPersona: "standard",
  themeName: "Sovkora",
  themeTagline: "Decoupled Intelligence",
  themeAccent: "zinc",
  themeLayout: "standard",
  theme: "system",
  tenantId: "orazaka-default",
};

/**
 * Shared form state for the Profile Appearance + Workspace tabs.
 *
 * Lifts the preference state formerly held inside the deleted `SettingsForm`,
 * wrapping {@link useSettings} (persistence) and {@link useAppearance} (instant theme).
 * Exposes a single `save()` and a `isDirty` flag that drives the sticky Save bar.
 */
export function useProfileForm() {
  const { settings, isLoading, updateSettings, isUpdating } = useSettings();
  const { setLocale, t } = useTranslation();
  const { setAppearance: applyTheme } = useAppearance();

  const [form, setForm] = React.useState<ProfileFormState>(EMPTY);
  const [baseline, setBaseline] = React.useState<ProfileFormState>(EMPTY);

  // Hydrate from persisted settings once they resolve.
  React.useEffect(() => {
    if (!settings) return;
    const next: ProfileFormState = {
      language: settings.language as Locale,
      aiPersona: settings.aiPersona,
      themeName: settings.themeName || EMPTY.themeName,
      themeTagline: settings.themeTagline || EMPTY.themeTagline,
      themeAccent: settings.themeAccent || EMPTY.themeAccent,
      themeLayout: settings.themeLayout || EMPTY.themeLayout,
      theme: toAppearance(settings.theme),
      tenantId: settings.tenantId || EMPTY.tenantId,
    };
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setBaseline(next);
    setForm(next);
  }, [settings]);

  // Accent options come from the live model catalog; the presets stand in until it answers or when it has no theme.
  const catalog = useQuery({
    queryKey: ["models-catalog", "theme"],
    queryFn: async () => {
      const session = await getSession();
      const res = await fetch("/api/v1/models/catalog", {
        headers: session?.user?.id ? { Authorization: `Bearer ${session.user.id}` } : {},
      });
      if (!res.ok) throw new Error("catalog unavailable");
      return ((await res.json()) as { category: string; modelName: string; modelLabel: string }[])
        .filter((m) => m.category === "theme")
        .map((m) => ({ value: m.modelName, label: m.modelLabel }));
    },
  });
  const availableThemes = React.useMemo(() => {
    if (catalog.data && catalog.data.length > 0) return catalog.data;
    if (catalog.isPending) return [];
    return THEME_ACCENTS.map((a) => {
      const key = THEME_ACCENT_LABELS[a].split(".")[1] as keyof typeof t.settings;
      return { value: a, label: (t.settings[key] as string) || a };
    });
  }, [catalog.data, catalog.isPending, t]);

  const setField = React.useCallback(<K extends keyof ProfileFormState>(key: K, value: ProfileFormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  }, []);

  /** Language changes apply to the live locale immediately (still persisted on save). */
  const setLanguage = React.useCallback(
    (value: Locale) => {
      setField("language", value);
      setLocale(value);
    },
    [setField, setLocale],
  );

  /** Theme changes apply instantly via the ThemeProvider (still persisted on save). */
  const setTheme = React.useCallback(
    (value: Appearance) => {
      setField("theme", value);
      applyTheme(value);
    },
    [setField, applyTheme],
  );

  const isDirty = React.useMemo(
    () => (Object.keys(form) as (keyof ProfileFormState)[]).some((k) => form[k] !== baseline[k]),
    [form, baseline],
  );

  const save = React.useCallback(() => {
    setBaseline(form);
    updateSettings(form);
  }, [form, updateSettings]);

  const discard = React.useCallback(() => {
    setForm(baseline);
    setLocale(baseline.language);
    applyTheme(baseline.theme);
  }, [baseline, setLocale, applyTheme]);

  return {
    form,
    setField,
    setLanguage,
    setTheme,
    availableThemes,
    isLoading,
    isUpdating,
    isDirty,
    save,
    discard,
  };
}

export type UseProfileForm = ReturnType<typeof useProfileForm>;
