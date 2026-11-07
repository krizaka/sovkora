"use client";

import { useState } from "react";
import { Icon } from "@krizaka/orazaka-design-system";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { useTranslation } from "@/core/context/LocaleContext";
import { formatCredits } from "@krizaka/orazaka-shared";
import { useWallet } from "@/features/billing/hooks/useWallet";

export interface LowBalanceBannerProps {
  /** Opens the top-up flow. */
  onTopUp?: () => void;
}

/**
 * Warns before the balance runs out, not after.
 *
 * Driven by `evt.wallet.low-balance` over SSE, so it appears the moment the
 * crossing happens rather than on the user's next navigation — by which point
 * they may already have started work that will be refused.
 *
 * Dismissible, and dismissal is per-session rather than permanent: the warning is
 * relevant again the next time they sign in with a balance still low, and a
 * banner that could be silenced forever would stop being a warning.
 *
 * A @krizaka/ui Alert (tone warning: announced at once, role "alert").
 */
export function LowBalanceBanner({ onTopUp }: LowBalanceBannerProps) {
  const { t } = useTranslation();
  const { available, isLow } = useWallet();
  const [dismissed, setDismissed] = useState(false);

  if (!isLow || dismissed) {
    return null;
  }

  return (
    <Alert
      tone="warning"
      icon={<Icon name="warning" />}
      title={t.billing.lowBalanceTitle}
      action={
        <>
          {onTopUp && (
            <Button size="sm" onClick={onTopUp}>
              {t.billing.topUp}
            </Button>
          )}
          <Button size="sm" variant="ghost" onClick={() => setDismissed(true)}>
            {t.billing.dismiss}
          </Button>
        </>
      }
    >
      {t.billing.lowBalance.replace("{credits}", formatCredits(available))}
    </Alert>
  );
}
