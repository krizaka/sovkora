"use client";

import * as React from "react";
import { Input, Select } from "@krizaka/ui/field";
import { SettingsRow, SettingsSection } from "@krizaka/ui/settings-layout";
import { useTranslation } from "@/core/context/LocaleContext";
import { AI_PERSONAS, AI_PERSONA_LABELS, type AiPersona } from "@/constants/settings.constants";
import type { UseProfileForm } from "@/features/profile/hooks/useProfileForm";

/**
 * Workspace section (admin-only) — tenant branding plus the default assistant persona, as settings rows. Edits
 * flow through the shared sticky Save bar.
 */
export function WorkspaceTab({ pf }: Readonly<{ pf: UseProfileForm }>) {
  const { t } = useTranslation();
  const { form, setField } = pf;

  return (
    <SettingsSection
      variant="flat"
      id="workspace"
      title={t.profile.workspaceTitle}
      description={t.profile.workspaceDesc}
    >
      <SettingsRow label={t.profile.rowAppName} description={t.profile.rowAppNameHelp} htmlFor="workspace-name">
        <Input
          id="workspace-name"
          className="w-64"
          value={form.themeName}
          onChange={(e) => setField("themeName", e.target.value)}
          placeholder={t.settings.appNamePlaceholder}
        />
      </SettingsRow>
      <SettingsRow label={t.profile.rowTagline} description={t.profile.rowTaglineHelp} htmlFor="workspace-tagline">
        <Input
          id="workspace-tagline"
          className="w-64"
          value={form.themeTagline}
          onChange={(e) => setField("themeTagline", e.target.value)}
          placeholder={t.settings.taglinePlaceholder}
        />
      </SettingsRow>
      <SettingsRow label={t.profile.rowTenant} description={t.profile.rowTenantHelp} htmlFor="workspace-tenant">
        <Input
          id="workspace-tenant"
          className="w-64 font-mono"
          value={form.tenantId}
          onChange={(e) => setField("tenantId", e.target.value)}
          placeholder={t.settings.tenantIdPlaceholder}
        />
      </SettingsRow>
      <SettingsRow label={t.profile.rowPersona} description={t.profile.rowPersonaHelp} htmlFor="workspace-persona">
        <Select
          id="workspace-persona"
          className="w-64"
          value={form.aiPersona}
          onChange={(e) => setField("aiPersona", e.target.value as AiPersona)}
        >
          {AI_PERSONAS.map((p) => (
            <option key={p} value={p}>
              {t.settings[AI_PERSONA_LABELS[p].split(".")[1] as keyof typeof t.settings] as string}
            </option>
          ))}
        </Select>
      </SettingsRow>
    </SettingsSection>
  );
}
