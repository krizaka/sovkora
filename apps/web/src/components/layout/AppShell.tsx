"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { CodeNameLine, OrazakaLogo } from "@krizaka/ui";
import { AppFrame, type FrameSection } from "@krizaka/ui/app-frame";
import { SettingsIcon, ShieldIcon, UserIcon } from "@krizaka/icons";
import { useAuth } from "@/core/hooks/useAuth";
import { useBrand } from "@/core/context/BrandContext";
import { useCodeName } from "@/core/context/CodeNameContext";
import { useTranslation } from "@/core/context/LocaleContext";
import { AppSectionId, AppSectionPlace, appSectionsFor, currentAppSection } from "@/core/config/app-sections";
import { NotificationBell } from "./NotificationBell";

const LOCALES = ["en", "fr"] as const;

/** The places the frame holds under the avatar (the shared account menu): Settings and Admin leave the rail. */
const ACCOUNT_PLACES: ReadonlySet<string> = new Set([AppSectionId.Settings, AppSectionId.Admin]);

/**
 * The signed-in frame of every page of the app — `@krizaka/ui` `AppFrame` (one rail, one top bar, one account menu for
 * the whole family). The sections come from core/config/app-sections.ts, their words from `t.appNav`; Settings and
 * Admin are the account menu's places (Settings, Administration), never repeated on the rail.
 */
export function AppShell({ children }: Readonly<{ children: React.ReactNode }>) {
  const { user, isAuthenticated, logout } = useAuth();
  const { locale, setLocale, t } = useTranslation();
  const [bellOpen, setBellOpen] = React.useState(false);
  const brand = useBrand();
  const codeName = useCodeName();
  const isAdmin = user?.role === "admin";
  const current = currentAppSection(usePathname());

  const sections: FrameSection[] = appSectionsFor(isAdmin)
    .filter((s) => s.id !== AppSectionId.Home && !ACCOUNT_PLACES.has(s.id) && s.place === AppSectionPlace.Main)
    .map((s) => ({
      id: s.id,
      label: t.appNav[s.id],
      href: s.href,
      icon: <s.Icon size={20} />,
    }));

  const initial = ((user?.name || user?.email || "U")[0] ?? "U").toUpperCase();

  return (
    <AppFrame
      name={brand}
      mark={<OrazakaLogo size={26} />}
      headerMark={<OrazakaLogo size={22} />}
      homeHref="/"
      accent="orazaka"
      chips={codeName ? <CodeNameLine className="hidden sm:inline">{codeName}</CodeNameLine> : undefined}
      label={t.appNav.label}
      sections={sections}
      currentId={current ?? undefined}
      locale={locale}
      linkAs={Link}
      notifications={isAuthenticated ? <NotificationBell bellOpen={bellOpen} onToggle={setBellOpen} /> : undefined}
      language={{
        value: locale,
        options: LOCALES.map((code) => ({
          value: code,
          label: code === "en" ? t.settings.english : t.settings.french,
        })),
        onChange: (code) => setLocale(code as (typeof LOCALES)[number]),
      }}
      account={{
        name: user?.name || undefined,
        email: user?.email || undefined,
        avatar: {
          defaultAvatar: {
            brand: "orazaka",
            seed: user?.id ?? user?.email ?? user?.name ?? undefined,
          },
          fallback: initial,
        },
        links: {
          profile: {
            href: "/settings/account",
            label: t.profile.title,
            icon: <UserIcon size={16} />,
          },
          settings: {
            href: "/settings/appearance",
            label: t.appNav.settings,
            icon: <SettingsIcon size={16} />,
          },
          ...(isAdmin
            ? {
                administration: {
                  href: "/dashboard/admin",
                  label: t.appNav.admin,
                  icon: <ShieldIcon size={16} />,
                },
              }
            : {}),
        },
        onSignOut: () => logout(),
      }}
    >
      {children}
    </AppFrame>
  );
}
