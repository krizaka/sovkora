import { AutomationsSection } from "@/features/automation/components/AutomationsSection";
import ConnectorCatalogue from "@/features/automation/components/ConnectorCatalogue";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Automations | Sovkora",
  description: "Run a Studio from a signed webhook or a schedule, and follow every run.",
};

/** The automation page: your automations (triggers and run history), then the connector catalogue. */
export default function AutomationPage() {
  return (
    <main className="ambient-grid min-h-full">
      <section className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6">
        <AutomationsSection />
        <ConnectorCatalogue />
      </section>
    </main>
  );
}
