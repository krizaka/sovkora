"use client";

import { cn } from "@krizaka/ui/cn";

import { useTranslation } from "@/core/context/LocaleContext";

/**
 * Sovkora says what it is, like Yomizaka and Akinaza: in development, still being tested. One line from the messages (en/fr),
 * on the hero and on the sign-in; no « ready » wording anywhere (krizaka/orazaka#516).
 */
export function DevelopmentStatus({ className = "" }: Readonly<{ className?: string }>) {
  const { t } = useTranslation();
  return (
    <p className={cn("max-w-[60ch] text-sm text-fg-secondary", className)} data-testid="development-status">
      {t.landing.status.development}
    </p>
  );
}
