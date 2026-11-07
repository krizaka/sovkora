import type { Metadata } from "next";
import { SupervisionScreen } from "./SupervisionScreen";

export const metadata: Metadata = { title: "Supervision · Sovkora Operations" };

/** `/` — the console opens on the platform's health: usage, credits, jobs, latency. */
export default function SupervisionPage() {
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
      <SupervisionScreen />
    </main>
  );
}
