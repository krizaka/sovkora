import { Suspense } from "react";
import type { Metadata } from "next";
import { PacksScreen } from "./PacksScreen";

export const metadata: Metadata = {
  title: "Plan & packs | Sovkora",
  description: "What your plan gives you, the packs you hold, and what another pack would add to your Studios.",
};

/**
 * « Plan & packs »: a thin route shell. The Suspense boundary is what `useSearchParams` asks for (`?pack=` opens a
 * pack's drawer).
 */
export default function PacksPage() {
  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-6 sm:px-6">
      <Suspense>
        <PacksScreen />
      </Suspense>
    </main>
  );
}
