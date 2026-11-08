import type { Metadata } from "next";
import { AccountsScreen } from "@/console/features/accounts/components/AccountsScreen";

export const metadata: Metadata = { title: "People · Sovkora Operations" };

/** `/users` — the platform's accounts: state, role, Law 25 export and erasure. */
export default function UsersPage() {
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
      <AccountsScreen />
    </main>
  );
}
