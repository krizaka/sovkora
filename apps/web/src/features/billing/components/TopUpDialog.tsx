"use client";

import { useQuery } from "@krizaka/query";
import { Button } from "@krizaka/ui/button";
import { Dialog } from "@krizaka/ui/dialog";
import { useTranslation } from "@/core/context/LocaleContext";
import { BillingApi } from "@/services/billing.api";
import { formatCredits, type Plan } from "@krizaka/orazaka-shared";
import { useMoney } from "@/core/hooks/useMoney";

export interface TopUpDialogProps {
  /** Whether the dialog is open. */
  open: boolean;
  /** Close it. */
  onClose: () => void;
}

/**
 * The way out of an empty balance.
 *
 * Shows the plans rather than a credit-pack picker, because the local phase sells
 * no packs yet: `billing_pack` exists but nothing links an actor to one, so
 * offering to buy one would be a button with nothing behind it. Upgrading is the
 * remedy that actually works today, and the refusal payload says so — the server
 * only offers `top_up_credits` to actors whose plan permits it.
 */
export function TopUpDialog({ open, onClose }: TopUpDialogProps) {
  const { t } = useTranslation();
  const money = useMoney();
  // The public plans are read once the dialog opens, and again only when they are stale.
  const loaded = useQuery({
    queryKey: ["billing-plans"],
    queryFn: () => BillingApi.fetchPlans(),
    enabled: open,
    retry: false,
  });
  const plans: Plan[] = (loaded.data ?? []).filter((plan) => plan.isPublic && plan.isActive);
  const isLoading = open && loaded.isFetching;

  return (
    <Dialog.Root open={open} onOpenChange={(next) => !next && onClose()}>
      <Dialog.Content closeLabel={t.billing.close}>
        <Dialog.Header>
          <Dialog.Title>{t.billing.topUpTitle}</Dialog.Title>
        </Dialog.Header>
        <Dialog.Body className="space-y-4">
          {isLoading && <p className="text-sm text-fg-muted">{t.billing.loading}</p>}

          {!isLoading && plans.length === 0 && <p className="text-sm text-fg-muted">{t.billing.noPlans}</p>}

          <ul className="space-y-2">
            {plans.map((plan) => (
              <li
                key={plan.planKey}
                className="flex items-center justify-between rounded-lg border border-border-subtle px-4 py-3"
              >
                <span>
                  <span className="block text-sm font-medium text-fg">{plan.label}</span>
                  <span className="block text-xs text-fg-muted">
                    {t.billing.perMonth.replace("{credits}", formatCredits(plan.monthlyCreditGrant))}
                  </span>
                </span>
                <span className="text-sm text-fg">{money(plan.priceCents, plan.currency)}</span>
              </li>
            ))}
          </ul>
        </Dialog.Body>
        <Dialog.Footer>
          <Button variant="ghost" onClick={onClose}>
            {t.billing.close}
          </Button>
        </Dialog.Footer>
      </Dialog.Content>
    </Dialog.Root>
  );
}
