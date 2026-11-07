"use client";

import { useAuthFlags } from "@/core/context/AuthFlagsContext";
import { useTranslation } from "@/core/context/LocaleContext";

export interface Entry {
  href: string;
  label: string;
}

/**
 * The two ways in of a public page, honest about the AUTH_* flags: while sign-up is closed the primary action reads
 * the documentation. Sign in is always offered — while it is closed `/login` shows the real sign-in screen, disabled,
 * with a calm banner and the way to the demo.
 */
export function useEntries() {
  const flags = useAuthFlags();
  const { t } = useTranslation();
  const closed = t.landing.closed;
  return {
    flags,
    primary: (label: string): Entry =>
      flags.signup ? { href: "/register", label } : { href: "/docs", label: closed.heroPrimary },
    secondary: (label: string, href = "/login"): Entry => ({ href, label }),
  };
}
