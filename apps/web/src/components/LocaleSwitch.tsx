"use client";

import { type Language, LanguageSwitch, languageNames } from "@krizaka/ui/language-switch";
import { usePathname } from "next/navigation";
import type { ComponentProps } from "react";
import { useTranslation } from "@/core/context/LocaleContext";
import type { Locale } from "@/core/context/translations.types";
import { localeHref } from "@/core/i18n/locale-negotiation";

const LOCALES: readonly Locale[] = ["en", "fr"];

/** A language link that sets the locale (the web client keeps it in its context and a cookie, not in the address). */
function LocaleLink({ onClick, ...props }: ComponentProps<"a">) {
  const { setLocale } = useTranslation();
  return (
    <a
      {...props}
      onClick={(e) => {
        onClick?.(e);
        if (props.hrefLang && !e.defaultPrevented) {
          e.preventDefault();
          setLocale(props.hrefLang as Locale);
        }
      }}
    />
  );
}

/** The props of the family's language switch for this app: the languages by their own names. The locale lives in the app's context (and a cookie), not in the address: a click
 * sets it and stays on the page, so the link's own address is only the fallback anchor. */
export function useLanguageSwitch(label: string) {
  const { locale } = useTranslation();
  const pathname = usePathname() ?? "/";
  // A real address for each language (`/fr/pricing`): a click still stays on the page, but a link is never `#`.
  const languages: Language[] = LOCALES.map((code) => ({
    code,
    name: languageNames[code] ?? code,
    href: localeHref(code, pathname),
  }));
  return { label, current: locale, languages, linkAs: LocaleLink };
}

/**
 * The language switch of the pages a visitor reads before signing in (sign-in, legal, contact) and of the footer: the
 * family's `LanguageSwitch` (a globe and the current code, or a row of the languages with `variant="inline"`).
 */
export function LocaleSwitch({
  variant = "menu",
  className,
}: Readonly<{ variant?: "menu" | "inline"; className?: string }>) {
  const { t } = useTranslation();
  return <LanguageSwitch {...useLanguageSwitch(t.auth.langSwitchLabel)} variant={variant} className={className} />;
}
