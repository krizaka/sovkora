import { redirect } from "next/navigation";
import { legacyTabTarget } from "@/features/profile/utils/settingsSections";

/**
 * `/settings` — the old entry point. Each section now has its own route (`/settings/account`, …); the former
 * `/settings?tab=<section>` keeps working and lands on the section's route.
 */
export default async function SettingsIndex({
  searchParams,
}: Readonly<{ searchParams: Promise<{ tab?: string | string[] }> }>) {
  const { tab } = await searchParams;
  redirect(legacyTabTarget(Array.isArray(tab) ? tab[0] : tab));
}
