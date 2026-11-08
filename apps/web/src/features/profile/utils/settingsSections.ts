/**
 * The settings sections and their routes. The URL is the state: `/settings/<section>`. The old
 * `/settings?tab=<section>` is redirected by {@link legacyTabTarget}.
 */
export const SETTINGS_SECTIONS = [
  "account",
  "appearance",
  "workspace",
  "integrations",
  "api-keys",
  "webhooks",
] as const;

export type SettingsSectionId = (typeof SETTINGS_SECTIONS)[number];

/** Sections only a workspace administrator sees. */
const ADMIN_ONLY: ReadonlySet<SettingsSectionId> = new Set(["workspace"]);

export function isSettingsSection(value: unknown): value is SettingsSectionId {
  return typeof value === "string" && (SETTINGS_SECTIONS as readonly string[]).includes(value);
}

/** The sections a person sees, in order. */
export function visibleSections(isAdmin: boolean): SettingsSectionId[] {
  return SETTINGS_SECTIONS.filter((id) => isAdmin || !ADMIN_ONLY.has(id));
}

/** The section to show for a requested id: an unknown or unauthorised one falls back to `account`. */
export function resolveSection(requested: string | undefined, isAdmin: boolean): SettingsSectionId {
  return isSettingsSection(requested) && visibleSections(isAdmin).includes(requested) ? requested : "account";
}

export function settingsHref(id: SettingsSectionId): string {
  return `/settings/${id}`;
}

/** Where the old `/settings?tab=<section>` leads. */
export function legacyTabTarget(tab: string | null | undefined): string {
  return settingsHref(isSettingsSection(tab) ? tab : "account");
}
