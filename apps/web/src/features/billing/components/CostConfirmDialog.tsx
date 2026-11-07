"use client";

import { Button } from "@krizaka/ui/button";
import { Dialog } from "@krizaka/ui/dialog";
import { useTranslation } from "@/core/context/LocaleContext";
import { formatCredits } from "@krizaka/orazaka-shared";
import { useCostEstimate } from "@/features/billing/hooks/useCostEstimate";

export interface CostConfirmDialogProps {
  /** Whether the dialog is open. */
  open: boolean;
  /** The capability about to run. */
  capability: string;
  /** The resolved model, when the user picked one. */
  modelName?: string | null;
  /** What the user is about to do, in their words. */
  actionLabel: string;
  /** Proceed. */
  onConfirm: () => void;
  /** Back out. */
  onCancel: () => void;
}

/**
 * Shows what an action costs before the user commits to it.
 *
 * The design states this is not a UI nicety (§12): an unexpected debit on a job
 * the user did not know was expensive is the number-one support ticket in credit
 * products. The figure is the pricebook's own estimate — the same one the hold
 * will reserve — so the quote and the charge cannot disagree.
 *
 * When the estimate cannot be fetched the dialog still confirms, without a price.
 * Blocking the action would turn a billing hiccup into an outage of the product;
 * showing a guessed number would be worse than showing none.
 *
 * A @krizaka/ui Dialog (Radix: focus trap, Escape, focus return); Escape and the close button cancel.
 */
export function CostConfirmDialog({
  open,
  capability,
  modelName,
  actionLabel,
  onConfirm,
  onCancel,
}: CostConfirmDialogProps) {
  const { t } = useTranslation();
  const { estimate, isLoading } = useCostEstimate(open ? capability : null, modelName);

  const unaffordable = estimate ? !estimate.affordable : false;

  return (
    <Dialog.Root open={open} onOpenChange={(next) => !next && onCancel()}>
      <Dialog.Content size="sm" closeLabel={t.billing.cancel}>
        <Dialog.Header>
          <Dialog.Title>{actionLabel}</Dialog.Title>
        </Dialog.Header>
        <Dialog.Body className="space-y-2">
          {isLoading && <p className="text-sm text-fg-muted">{t.billing.estimating}</p>}

          {!isLoading && estimate && (
            <>
              <p className="text-sm text-fg">
                {t.billing.estimate.replace("{credits}", formatCredits(estimate.estimateCredits))}
              </p>
              <p className="text-sm text-fg-muted">
                {t.billing.available.replace("{credits}", formatCredits(estimate.availableCredits))}
              </p>
              {unaffordable && <p className="text-sm text-danger">{t.billing.insufficient}</p>}
            </>
          )}

          {!isLoading && !estimate && <p className="text-sm text-fg-muted">{t.billing.noEstimate}</p>}
        </Dialog.Body>
        <Dialog.Footer>
          <Button variant="ghost" onClick={onCancel}>
            {t.billing.cancel}
          </Button>
          <Button onClick={onConfirm} disabled={unaffordable}>
            {t.billing.confirm}
          </Button>
        </Dialog.Footer>
      </Dialog.Content>
    </Dialog.Root>
  );
}
