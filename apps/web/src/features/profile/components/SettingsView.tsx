"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@krizaka/ui/button";
import { SettingsLayout, type SettingsNavItem } from "@krizaka/ui/settings-layout";
import { Icon, type IconName } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { useProfile } from "@/features/profile/hooks/useProfile";
import { useProfileForm } from "@/features/profile/hooks/useProfileForm";
import {
  resolveSection,
  settingsHref,
  visibleSections,
  type SettingsSectionId,
} from "@/features/profile/utils/settingsSections";
import { AccountTab } from "./AccountTab";
import { AppearanceTab } from "./AppearanceTab";
import { WorkspaceTab } from "./WorkspaceTab";
import { IntegrationsTab } from "./IntegrationsTab";
import { ApiKeysTab } from "./ApiKeysTab";
import { WebhooksTab } from "./WebhooksTab";

const ICONS: Record<SettingsSectionId, IconName> = {
  account: "profile",
  appearance: "sun",
  workspace: "system",
  integrations: "mcp",
  "api-keys": "key",
  webhooks: "mcp",
};

/**
 * The settings: a sub-navigation of sections (chips on a phone) and the current section's rows in one calm column.
 * The URL is the section (`/settings/<section>`); Workspace is admin-only. A sticky Save bar appears whenever
 * appearance or workspace edits are pending.
 */
export function SettingsView({ section }: Readonly<{ section: string }>) {
  const { t } = useTranslation();
  const { profile, isLoading, error } = useProfile();
  const pf = useProfileForm();
  const router = useRouter();

  const isAdmin = profile?.authorities?.includes("ROLE_ADMIN") ?? false;
  const active = resolveSection(section, isAdmin);

  // An unknown or unauthorised section (e.g. /settings/workspace for a member) lands on the account.
  React.useEffect(() => {
    if (profile && active !== section) router.replace(settingsHref(active));
  }, [profile, active, section, router]);

  if (isLoading) {
    return (
      <section className="space-y-6" aria-busy="true">
        <div className="h-8 w-40 animate-pulse rounded-md bg-surface-2" />
        <div className="h-64 animate-pulse rounded-md bg-surface-2" />
      </section>
    );
  }

  if (error || !profile) {
    return (
      <section className="rounded-xl border border-danger/20 bg-danger/5 p-4 text-danger">
        <p className="font-semibold">{t.profile.failedLoad}</p>
        <p className="text-sm opacity-80">{(error as Error)?.message || t.profile.unauthError}</p>
      </section>
    );
  }

  const labels: Record<SettingsSectionId, string> = {
    account: t.profile.tabs.account,
    appearance: t.profile.tabs.appearance,
    workspace: t.profile.tabs.workspace,
    integrations: t.profile.tabs.integrations,
    "api-keys": t.profile.tabs.apiKeys,
    webhooks: t.profile.tabs.webhooks,
  };
  const nav: SettingsNavItem[] = visibleSections(isAdmin).map((id) => ({
    id,
    label: labels[id],
    href: settingsHref(id),
    icon: <Icon name={ICONS[id]} size={16} />,
    current: id === active,
  }));

  return (
    <>
      <SettingsLayout
        variant="flat"
        title={t.profile.pageTitle}
        description={t.profile.pageDesc}
        nav={nav}
        navLabel={t.profile.navLabel}
        linkAs={Link}
        className="pb-24"
      >
        {active === "account" && <AccountTab profile={profile} t={t} />}
        {active === "appearance" && <AppearanceTab pf={pf} />}
        {active === "workspace" && isAdmin && <WorkspaceTab pf={pf} />}
        {active === "integrations" && <IntegrationsTab />}
        {active === "api-keys" && <ApiKeysTab />}
        {active === "webhooks" && <WebhooksTab />}
      </SettingsLayout>

      {/* Sticky Save bar: pending appearance/workspace edits */}
      {pf.isDirty && (
        <div className="sticky bottom-4 z-20 flex items-center justify-between gap-3 rounded-xl border border-border-default bg-surface-1/92 p-3 pl-4 shadow-lg animate-in fade-in slide-in-from-bottom-2 duration-200">
          <span className="text-sm text-fg-secondary">{t.profile.unsavedChanges}</span>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" onClick={pf.discard} disabled={pf.isUpdating}>
              {t.profile.discard}
            </Button>
            <Button size="sm" onClick={pf.save} disabled={pf.isUpdating}>
              {pf.isUpdating ? t.settings.saving : t.profile.saveChanges}
            </Button>
          </div>
        </div>
      )}
    </>
  );
}
