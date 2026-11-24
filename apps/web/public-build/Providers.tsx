"use client";

import type { ReactNode } from "react";
import { MotionObserver } from "@krizaka/ui";
import { PageTransition } from "@krizaka/ui/motion";
import { ThemeProvider } from "@krizaka/ui/theme";
import { QueryProvider } from "@/core/providers/QueryProvider";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import { BrandProvider } from "@/core/context/BrandContext";
import { LocaleProvider } from "@/core/context/LocaleContext";
import { AppCharts } from "@/core/components/AppCharts";
import { DEFAULT_BRAND } from "@/core/brand/brand";

/** Every way in closed: the export has no sign-in and no sign-up. */
const NO_ACCOUNTS = { login: false, signup: false, github: false, google: false };

/**
 * The provider tree of the public export: the theme, the language and the charts — no session, no tenant, no job stream, no
 * command palette, no toaster (nothing that calls the API).
 */
export function PublicProviders({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <AuthFlagsProvider flags={NO_ACCOUNTS}>
      <BrandProvider name={DEFAULT_BRAND}>
        <ThemeProvider defaultMode="system">
          <QueryProvider>
            <LocaleProvider>
              <MotionObserver />
              <AppCharts>
                <PageTransition>{children}</PageTransition>
              </AppCharts>
            </LocaleProvider>
          </QueryProvider>
        </ThemeProvider>
      </BrandProvider>
    </AuthFlagsProvider>
  );
}
