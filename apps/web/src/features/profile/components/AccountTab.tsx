"use client";

import * as React from "react";
import { Avatar } from "@krizaka/ui/avatar";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { SettingsRow, SettingsSection } from "@krizaka/ui/settings-layout";
import type { UserProfile } from "@/services/profile.api";
import { useTranslation, type TranslationDictionary } from "@/core/context/LocaleContext";
import { contactUrl } from "@/core/config/contact";
import { useAuth } from "@/core/hooks/useAuth";
import { CopyableField } from "./ProfileViewParts";

interface AccountTabProps {
  profile: UserProfile;
  t: TranslationDictionary;
}

/** The plan a person is on, from their authorities (a quiet line, not a badge). */
function planName(profile: UserProfile, t: TranslationDictionary): string {
  const auths = profile.authorities ?? [];
  if (auths.includes("ROLE_ADMIN")) return t.profile.enterpriseTier;
  if (auths.includes("ROLE_USER")) return t.profile.premiumTier;
  return t.profile.freeTier;
}

/**
 * Account section — who you are as settings rows (a small avatar, the name and e-mail, the role, the plan), the
 * danger zone, and, last, "Advanced": the account id (support only) and the raw preferences.
 */
export function AccountTab({ profile, t }: Readonly<AccountTabProps>) {
  const { logout } = useAuth();
  const { locale } = useTranslation();
  const [showPrefs, setShowPrefs] = React.useState(false);
  const seed = profile.id || profile.email || profile.username;

  return (
    <>
      <SettingsSection variant="flat" id="account" title={t.profile.tabs.account} description={t.profile.accountDesc}>
        <SettingsRow
          label={
            <span className="flex items-center gap-3">
              <Avatar
                size="md"
                defaultAvatar={{ brand: "orazaka", seed }}
                fallback={(profile.username || "U")[0].toUpperCase()}
              />
              <span className="flex min-w-0 flex-col">
                <span className="truncate text-sm font-medium text-fg">{profile.username}</span>
                <span className="truncate text-sm font-normal text-fg-secondary">{profile.email}</span>
              </span>
            </span>
          }
        />
        <SettingsRow label={t.profile.assignedAuth} description={t.profile.roleHelp}>
          {profile.authorities && profile.authorities.length > 0 ? (
            profile.authorities.map((auth) => <Badge key={auth}>{t.profile.roles[auth] ?? auth}</Badge>)
          ) : (
            <span className="text-fg-muted">{t.profile.noAuth}</span>
          )}
        </SettingsRow>
        <SettingsRow label={t.profile.planLabel} description={t.profile.planHelp}>
          <span className="text-fg-secondary">{planName(profile, t)}</span>
          <a className="text-fg-accent underline-offset-2 hover:underline" href={contactUrl(locale)}>
            {t.profile.planLink}
          </a>
        </SettingsRow>
      </SettingsSection>

      <SettingsSection variant="flat" id="danger" title={t.profile.dangerTitle} description={t.profile.dangerDesc}>
        <SettingsRow label={t.profile.signOut} description={t.profile.signOutHelp}>
          <Button variant="secondary" size="sm" onClick={logout}>
            {t.profile.signOut}
          </Button>
        </SettingsRow>
      </SettingsSection>

      <SettingsSection
        variant="flat"
        id="advanced"
        title={t.profile.advancedTitle}
        description={t.profile.advancedDesc}
      >
        <SettingsRow label={t.profile.userId} description={t.profile.accountIdHelp}>
          <CopyableField value={profile.id} />
        </SettingsRow>
        <SettingsRow label={t.profile.prefMetadata} description={t.profile.prefDesc}>
          <Button variant="ghost" size="sm" aria-expanded={showPrefs} onClick={() => setShowPrefs((v) => !v)}>
            {showPrefs ? t.profile.hidePreferences : t.profile.showPreferences}
          </Button>
        </SettingsRow>
        {showPrefs && (
          <pre className="max-h-64 overflow-auto py-4 font-mono text-xs leading-relaxed text-fg-secondary scrollbar-thin">
            {JSON.stringify(profile.preferences, null, 2)}
          </pre>
        )}
      </SettingsSection>
    </>
  );
}
