import type { Metadata } from "next";
import { PricingEditions } from "@/features/landing/components/PricingEditions";
import { loadPublicPrices } from "@/features/landing/utils/public-prices";
import { translations } from "@/core/context/translations";

const m = translations.en.pricing.meta;

export const metadata: Metadata = {
  title: m.title,
  description: m.description,
  alternates: { canonical: "/pricing" },
};

/** `/pricing` — the editions of Sovkora (krizaka/orazaka#447, #417). No price is typed: an amount appears only when billing's public price catalogue publishes it. */
export default async function Page() {
  return <PricingEditions prices={await loadPublicPrices()} />;
}
