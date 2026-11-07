import { AppShell } from "@/components/layout/AppShell";

/**
 * The application shell of the Studios, Packs and Automation sections: the same frame as the dashboard, chat and
 * profile, so moving between sections never drops the navigation.
 */
export default function ProtectedLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <AppShell>
      <section className="flex-1 overflow-auto scrollbar-thin">{children}</section>
    </AppShell>
  );
}
