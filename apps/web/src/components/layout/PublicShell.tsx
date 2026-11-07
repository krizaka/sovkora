"use client";

import type { ReactNode } from "react";
import { useSelectedLayoutSegment } from "next/navigation";
import { Providers } from "@/core/providers/Providers";
import { CodeNameProvider } from "@/core/context/CodeNameContext";
import { StageNotice } from "@/components/StageNotice";
import type { AuthFlags } from "@/core/config/auth-flags";

/**
 * The public site's shell (providers, the development-version banner) — skipped for the operator console (admin.orazaka.com), which has
 * its own in `app/console/layout.tsx`. The segment is read from the rendered tree, so it follows the proxy's rewrite
 * (`/studios` on the admin host is `/console/studios`) and the public pages stay static.
 */
export function PublicShell({
  authFlags,
  brand,
  showDevNotice,
  children,
}: Readonly<{ authFlags: AuthFlags; brand: string; showDevNotice: boolean; children: ReactNode }>) {
  if (useSelectedLayoutSegment() === "console") return <>{children}</>;
  return (
    <Providers authFlags={authFlags} brand={brand}>
      <StageNotice show={showDevNotice} />
      <CodeNameProvider dev={showDevNotice}>{children}</CodeNameProvider>
    </Providers>
  );
}
