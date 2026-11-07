/**
 * The sections of the signed-in app — one list read by the left rail (desktop), the drawer (phone) and the ⌘K
 * palette, in this order and with these words (`t.appNav[id]`). The same sections and names as the mobile tab bar and
 * the Mac app, which embeds this client (docs/ux/information-architecture.md of the Sovkora workspace): the phone's tabs
 * (Studios · Chat · Jobs · Settings) are this list without Home, Packs and Usage, in the same order.
 *
 * The rail holds where you work (`main`); the account's own place (Settings) and the console (Admin) sit at its foot.
 * The user menu under the avatar holds only who you are and Sign out — nothing the rail already has.
 */
import type { ComponentType } from "react";
import type { IconName } from "@krizaka/orazaka-design-system";
import {
  ChatIcon,
  ClockIcon,
  CreditsIcon,
  HomeIcon,
  PackIcon,
  SettingsIcon,
  ShieldIcon,
  StudioIcon,
  type IconProps,
} from "@krizaka/icons";

export const AppSectionId = {
  Home: "home",
  Chat: "chat",
  Studios: "studios",
  Packs: "packs",
  Jobs: "jobs",
  Usage: "usage",
  Settings: "settings",
  Admin: "admin",
} as const;
export type AppSectionId = (typeof AppSectionId)[keyof typeof AppSectionId];

export const AppSectionPlace = { Main: "main", Foot: "foot" } as const;
export type AppSectionPlace = (typeof AppSectionPlace)[keyof typeof AppSectionPlace];

export interface AppSection {
  id: AppSectionId;
  href: string;
  Icon: ComponentType<IconProps>;
  /** The design system's registry name, for the ⌘K palette. */
  paletteIcon: IconName;
  place: AppSectionPlace;
  adminOnly?: boolean;
}

export const APP_SECTIONS: readonly AppSection[] = [
  { id: AppSectionId.Home, href: "/", Icon: HomeIcon, paletteIcon: "dashboard", place: AppSectionPlace.Main },
  { id: AppSectionId.Studios, href: "/studios", Icon: StudioIcon, paletteIcon: "studio", place: AppSectionPlace.Main },
  { id: AppSectionId.Chat, href: "/chat", Icon: ChatIcon, paletteIcon: "chat", place: AppSectionPlace.Main },
  { id: AppSectionId.Jobs, href: "/jobs", Icon: ClockIcon, paletteIcon: "history", place: AppSectionPlace.Main },
  { id: AppSectionId.Packs, href: "/packs", Icon: PackIcon, paletteIcon: "layers", place: AppSectionPlace.Main },
  { id: AppSectionId.Usage, href: "/usage", Icon: CreditsIcon, paletteIcon: "spark", place: AppSectionPlace.Main },
  {
    id: AppSectionId.Settings,
    href: "/settings",
    Icon: SettingsIcon,
    paletteIcon: "profile",
    place: AppSectionPlace.Foot,
  },
  {
    id: AppSectionId.Admin,
    href: "/dashboard/admin",
    Icon: ShieldIcon,
    paletteIcon: "admin",
    place: AppSectionPlace.Foot,
    adminOnly: true,
  },
];

/** The sections this user sees (Admin only for an admin). */
export function appSectionsFor(isAdmin: boolean): readonly AppSection[] {
  return APP_SECTIONS.filter((s) => !s.adminOnly || isAdmin);
}

/** The one section the page belongs to — Home only on `/` itself, every other one for its path and what is under it. */
export function currentAppSection(pathname: string | null | undefined): AppSectionId | null {
  const p = pathname ?? "";
  if (p === "/") return AppSectionId.Home;
  const hit = APP_SECTIONS.find((s) => s.href !== "/" && (p === s.href || p.startsWith(`${s.href}/`)));
  return hit?.id ?? null;
}
