"use client";

import Link from "next/link";
import { LockIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";
import { useTranslation } from "@/core/context/LocaleContext";
import type { LockReason } from "@krizaka/orazaka-shared";

interface StudioLockNoticeProps {
  reason: LockReason;
  packKey: string | null;
}

/**
 * The upsell shown in place of the install button.
 *
 * The three reasons lead to three different actions, which is the whole point of
 * distinguishing them: buy this pack, change plan, or wait — and `UNKNOWN`
 * offers no action at all, because billing being unreachable is not the user's
 * problem to solve and asking them to pay for something they may already own is
 * the one mistake that costs trust (ADR-034 §8.3).
 */
export function StudioLockNotice({ reason, packKey }: Readonly<StudioLockNoticeProps>) {
  const { t } = useTranslation();

  if (reason === "NONE") {
    return null;
  }

  const copy: Record<Exclude<LockReason, "NONE">, string> = {
    REQUIRES_PURCHASE: t.studio.lockedPurchase,
    REQUIRES_PLAN: t.studio.lockedPlan,
    UNKNOWN: t.studio.lockedUnknown,
  };

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-warning/40 bg-warning/10 p-4">
      <p className="flex items-center gap-2 text-sm text-fg">
        <LockIcon size={16} className="flex-shrink-0 text-fg-warning" />
        {copy[reason]}
      </p>

      {reason === "REQUIRES_PURCHASE" && packKey && (
        <Button asChild variant="primary" size="sm" className="self-start">
          <Link href={`/packs?pack=${encodeURIComponent(packKey)}`}>{t.studio.buyPackage}</Link>
        </Button>
      )}

      {reason === "REQUIRES_PLAN" && (
        <Button asChild variant="primary" size="sm" className="self-start">
          <Link href="/usage">{t.studio.upgradePlan}</Link>
        </Button>
      )}
    </div>
  );
}
