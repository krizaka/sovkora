"use client";

import type { ReactNode } from "react";
import { SessionProvider } from "next-auth/react";
import { ChartsProvider } from "@krizaka/charts/provider";
import { MotionObserver } from "@krizaka/ui/motion";
import { ThemeProvider } from "@krizaka/ui/theme";
import { Toaster } from "@krizaka/ui/toast";
import { QueryProvider } from "@/core/providers/QueryProvider";
import { ConsoleLocaleProvider } from "@/console/core/providers/ConsoleLocale";

/**
 * The console's providers: the organisation's theme (dark by default, light and system on the toggle), the session,
 * the server-state cache (TanStack Query, for the screens that read through `useQuery`), the charts' settings (the Sovkora palette, figures in English), the motion conductor, the platform Toaster and the
 * language of the translated screens (the browser's, ConsoleLocale).
 */
export function Providers({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <ThemeProvider defaultMode="dark">
      <SessionProvider>
        <QueryProvider>
          <ChartsProvider locale="en-US" brand="orazaka">
            <MotionObserver />
            <ConsoleLocaleProvider>{children}</ConsoleLocaleProvider>
            <Toaster label="Notifications" closeLabel="Dismiss" />
          </ChartsProvider>
        </QueryProvider>
      </SessionProvider>
    </ThemeProvider>
  );
}
