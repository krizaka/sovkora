import type { Metadata } from "next";
import { PricingEditions } from "@/features/landing/components/PricingEditions";
import { translations } from "@/core/context/translations";
import { BUILD_LOCALE } from "@public-build/build-locale";

const m = translations[BUILD_LOCALE].pricing.meta;

/** The title and description in the build's language, naming only what is on offer. */
export const metadata: Metadata = { title: m.title, description: m.publicDescription, alternates: { canonical: "./" } };

/** `/fr/pricing`, `/en/pricing` of the public export: the editions on offer, with no price read from the billing service at build. */
export default function Page() {
  return <PricingEditions />;
}
