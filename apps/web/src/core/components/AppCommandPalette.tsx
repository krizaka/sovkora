"use client";

import { useRouter } from "next/navigation";
import { CommandPalette, type CommandPaletteItem } from "@krizaka/orazaka-design-system";
import { useAuth } from "@/core/hooks/useAuth";
import { useTranslation } from "@/core/context/LocaleContext";
import { AppSectionPlace, appSectionsFor } from "@/core/config/app-sections";

/**
 * The ⌘K palette of the web client: the design system's `CommandPalette` (a composite on @krizaka/ui/command) given
 * this app's sections (core/config/app-sections.ts) — the rail's, in the same words and order.
 */
export function AppCommandPalette() {
  const router = useRouter();
  const { user } = useAuth();
  const { t } = useTranslation();
  const go = t.commandPalette.sectionGo;

  const commands: CommandPaletteItem[] = appSectionsFor(user?.role === "admin").map((s) => ({
    id: s.id,
    label: t.appNav[s.id],
    icon: s.paletteIcon,
    href: s.href,
    section: s.place === AppSectionPlace.Foot ? t.commandPalette.sectionAccount : go,
  }));

  return <CommandPalette commands={commands} labels={t.commandPalette} onNavigate={(href) => router.push(href)} />;
}
