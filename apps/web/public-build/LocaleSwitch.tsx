"use client";

import { type Language, LanguageSwitch, languageNames } from "@krizaka/ui/language-switch";
import { usePathname } from "next/navigation";
import { useTranslation } from "@/core/context/LocaleContext";
import type { Locale } from "@/core/context/translations.types";

const LOCALES: readonly Locale[] = ["en", "fr"];

/** The language switch of the public export (an alias of `next.config.public.mjs`): each language is a plain link to the same page in its own build (`/en/docs`). */
export function useLanguageSwitch(label: string) {
  const { locale } = useTranslation();
  const pathname = usePathname() ?? "/";
  const languages: Language[] = LOCALES.map((code) => ({
    code,
    name: languageNames[code] ?? code,
    href: `/${code}${pathname}`,
  }));
  return { label, current: locale, languages };
}

export function LocaleSwitch({
  variant = "menu",
  className,
}: Readonly<{ variant?: "menu" | "inline"; className?: string }>) {
  const { t } = useTranslation();
  return <LanguageSwitch {...useLanguageSwitch(t.auth.langSwitchLabel)} variant={variant} className={className} />;
}
