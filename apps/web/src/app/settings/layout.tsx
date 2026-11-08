"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/layout/AppShell";
import { useAuth } from "@/core/hooks/useAuth";

/**
 * The frame of every settings route: the app's frame around a calm, scrolling column. Signed-out
 * visitors go to the sign-in page.
 */
export default function SettingsFrame({ children }: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();

  React.useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  if (isLoading || !isAuthenticated) return null;

  return (
    <AppShell>
      <main className="flex-1 overflow-auto p-4 scrollbar-thin sm:p-6">
        <div className="mx-auto max-w-5xl">
          <React.Suspense fallback={null}>{children}</React.Suspense>
        </div>
      </main>
    </AppShell>
  );
}
