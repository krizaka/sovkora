"use client";

import { ReactNode } from "react";
import { SessionProvider } from "next-auth/react";
import { MotionObserver } from "@krizaka/ui";
import { PageTransition } from "@krizaka/ui/motion";
import { ThemeProvider } from "@krizaka/ui/theme";
import { QueryProvider } from "./QueryProvider";
import { BrandProvider } from "@/core/context/BrandContext";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import type { AuthFlags } from "@/core/config/auth-flags";
import { TenantProvider } from "@/core/context/TenantContext";
import { LocaleProvider } from "@/core/context/LocaleContext";
import { JobStreamProvider } from "@/core/context/JobStreamContext";
import { AppToaster } from "@/core/components/AppToaster";
import { AppCommandPalette } from "@/core/components/AppCommandPalette";
import { AppCharts } from "@/core/components/AppCharts";

interface ProvidersProps {
  children: ReactNode;
  /** AUTH_LOGIN_ENABLED / AUTH_SIGNUP_ENABLED, read on the server. */
  authFlags: AuthFlags;
  /** The product name (`brand.displayName`), read on the server. */
  brand: string;
}

/**
 * Unified application provider tree wrapping all required context contexts.
 *
 * The theme is the organisation's (@krizaka/ui/theme): <ThemeScript /> in the root layout applies the persisted
 * choice before the first paint, this provider keeps it; {@link useAppearance} maps it to the profile's value.
 *
 * <AppCharts> gives every chart the reader's locale, the Sovkora palette and its words.
 *
 * <MotionObserver /> is mounted once here: every `[data-reveal]` of the app (the landing and the signed-in screens)
 * rises into view. Nothing follows the pointer (krizaka-ui#393).
 *
 * @param props The provider tree properties.
 * @returns The wrapped ReactNode provider layout.
 */
export function Providers({ children, authFlags, brand }: Readonly<ProvidersProps>) {
  // Sign-in closed: there is no session to look for — `session={null}` keeps next-auth from calling /api/auth/session.
  const sessionProps = authFlags.login ? {} : { session: null, refetchOnWindowFocus: false, refetchInterval: 0 };
  return (
    <AuthFlagsProvider flags={authFlags}>
      <BrandProvider name={brand}>
        <ThemeProvider defaultMode="system">
          <SessionProvider {...sessionProps}>
            <QueryProvider>
              <LocaleProvider>
                <TenantProvider>
                  <JobStreamProvider>
                    <AppCommandPalette />
                    <MotionObserver />
                    <AppCharts>
                      <PageTransition>{children}</PageTransition>
                    </AppCharts>
                    <AppToaster />
                  </JobStreamProvider>
                </TenantProvider>
              </LocaleProvider>
            </QueryProvider>
          </SessionProvider>
        </ThemeProvider>
      </BrandProvider>
    </AuthFlagsProvider>
  );
}
