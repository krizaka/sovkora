import type { Metadata } from "next";
import { AuditScreen } from "@/console/features/accounts/components/AuditScreen";

export const metadata: Metadata = { title: "Audit log · Sovkora Operations" };

/** `/audit` — the audit trail of account administration. */
export default function AuditPage() {
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
      <AuditScreen />
    </main>
  );
}
