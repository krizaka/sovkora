import type { Metadata } from "next";
import { CreditsScreen } from "./CreditsScreen";

export const metadata: Metadata = {
  title: "Credits | Sovkora",
  description: "Your balance, where it comes from, and what your work used — read from your own jobs and Studio runs.",
};

/** The credits & usage route: a thin shell over client state (the wallet is pushed over SSE). */
export default function BillingPage() {
  return (
    <main className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6">
      <CreditsScreen />
    </main>
  );
}
