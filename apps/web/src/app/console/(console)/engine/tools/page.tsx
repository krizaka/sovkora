import type { Metadata } from "next";
import { headers } from "next/headers";
import { pickLocale } from "@/console/core/copy/closed";
import { FeatureToolsScreen } from "@/console/features/engine/components/FeatureToolsScreen";

export const metadata: Metadata = { title: "Tool rounds · Sovkora Operations" };

/** `/engine/tools` — the per-feature tool-round bounds, edited in place, and the feature routes (read only). */
export default async function FeatureToolsPage() {
  const locale = pickLocale((await headers()).get("accept-language"));
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
      <FeatureToolsScreen locale={locale} />
    </main>
  );
}
