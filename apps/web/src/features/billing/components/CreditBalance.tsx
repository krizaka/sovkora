"use client";

import { Badge, Icon } from "@krizaka/orazaka-design-system";
import { formatCredits } from "@krizaka/orazaka-shared";
import { useWallet } from "@/features/billing/hooks/useWallet";

/**
 * The balance in the header.
 *
 * Shows spendable credits, not the raw total: credits held by in-flight work are
 * not available to spend, and a header that counted them would tell the user they
 * can afford something the hold will refuse.
 */
export function CreditBalance() {
  const { available, isLow, isLoading } = useWallet();

  if (isLoading) {
    return <span className="text-sm text-fg-muted">…</span>;
  }

  return (
    <Badge variant={isLow ? "warning" : "default"}>
      <span className="inline-flex items-center gap-1.5">
        <Icon name="spark" className="h-3.5 w-3.5" />
        {formatCredits(available)}
      </span>
    </Badge>
  );
}
