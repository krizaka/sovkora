import type { Metadata } from "next";
import { OperationsScreen } from "@/console/features/operations/components/OperationsScreen";

export const metadata: Metadata = { title: "Operations · Sovkora Operations" };

/** `/operations` — dependencies, queue depth, recent errors, runs and the deployed version. */
export default function OperationsPage() {
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
      <OperationsScreen />
    </main>
  );
}
