"use client";

import * as React from "react";
import { Select } from "@krizaka/ui/field";
import { SettingsRow, SettingsSection } from "@krizaka/ui/settings-layout";
import { useTranslation } from "@/core/context/LocaleContext";
import { THEME_LAYOUTS, THEME_LAYOUT_LABELS, type ThemeAccent, type ThemeLayout } from "@/constants/settings.constants";
import type { Locale } from "@/core/context/translations.types";
import { AppearancePicker } from "./AppearancePicker";
import type { UseProfileForm } from "@/features/profile/hooks/useProfileForm";

/**
 * Appearance section — the theme as radio cards with a miniature of each, then accent, density and language as
 * settings rows. All controls share {@link UseProfileForm} so edits surface in the sticky Save bar.
 */
export function AppearanceTab({ pf }: Readonly<{ pf: UseProfileForm }>) {
  const { t } = useTranslation();
  const { form, setField, setTheme, setLanguage, availableThemes } = pf;

  return (
    <SettingsSection
      variant="flat"
      id="appearance"
      title={t.profile.appearanceTitle}
      description={t.profile.appearanceDesc}
    >
      <div className="py-4">
        <AppearancePicker theme={form.theme} onThemeChange={setTheme} />
      </div>
      <SettingsRow label={t.profile.rowAccent} description={t.profile.rowAccentHelp} htmlFor="appearance-accent">
        <Select
          id="appearance-accent"
          className="w-48"
          value={form.themeAccent}
          onChange={(e) => setField("themeAccent", e.target.value as ThemeAccent)}
        >
          {availableThemes.map((th) => (
            <option key={th.value} value={th.value}>
              {th.label}
            </option>
          ))}
        </Select>
      </SettingsRow>
      <SettingsRow label={t.profile.rowDensity} description={t.profile.rowDensityHelp} htmlFor="appearance-layout">
        <Select
          id="appearance-layout"
          className="w-48"
          value={form.themeLayout}
          onChange={(e) => setField("themeLayout", e.target.value as ThemeLayout)}
        >
          {THEME_LAYOUTS.map((l) => (
            <option key={l} value={l}>
              {t.settings[THEME_LAYOUT_LABELS[l].split(".")[1] as keyof typeof t.settings] as string}
            </option>
          ))}
        </Select>
      </SettingsRow>
      <SettingsRow label={t.profile.rowLanguage} description={t.profile.rowLanguageHelp} htmlFor="appearance-language">
        <Select
          id="appearance-language"
          className="w-48"
          value={form.language}
          onChange={(e) => setLanguage(e.target.value as Locale)}
        >
          <option value="en">{t.settings.english}</option>
          <option value="fr">{t.settings.french}</option>
        </Select>
      </SettingsRow>
    </SettingsSection>
  );
}
