"use client";

import type { StudioPricing } from "@krizaka/orazaka-shared";
import { useTranslation } from "@/core/context/LocaleContext";

/**
 * How each pricing model is named to the actor.
 *
 * Held in one place because the card and the detail page must agree: a Studio shown as
 * "Inclus" in the catalogue and "Payant" on its own page is not a wording inconsistency,
 * it is a pricing claim the actor will act on.
 *
 * @returns the label for each pricing model, in the active locale
 */
export function usePricingLabel(): Record<StudioPricing, string> {
  const { t } = useTranslation();

  return {
    FREE: t.studio.pricingFree,
    INCLUDED: t.studio.pricingIncluded,
    PAID: t.studio.pricingPaid,
  };
}
