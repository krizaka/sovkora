import type { Metadata } from "next";
import { LawScreen } from "@/console/features/law/components/LawScreen";

export const metadata: Metadata = { title: "Law · Sovkora Operations" };

/** `/law` — Law → Proposals (ADR-089 §8): the review queue and promotion of the rules from the law, the corpus, the demand. */
export default function LawPage() {
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
      <LawScreen />
    </main>
  );
}
