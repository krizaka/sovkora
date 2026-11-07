import type { Metadata } from "next";
import { SiteFooter } from "@/console/components/SiteFooter";
import { Providers } from "@/console/core/providers/Providers";

export const metadata: Metadata = {
  title: "Sovkora Operations",
  description: "Supervise and govern your Sovkora platform: usage, credits, prices and Studios.",
  // An administration console: never indexed. Not canonical to the public site either.
  robots: { index: false, follow: false },
  alternates: { canonical: null },
  openGraph: null,
  twitter: null,
};

/**
 * The operator console (admin.orazaka.com, rewritten under /console by the proxy): its own providers and footer in
 * place of the public shell (`PublicShell` skips itself for this segment). Tokens, fonts and the theme script come
 * from the root layout, as everywhere in this app.
 */
export default function ConsoleRootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <div className="min-h-screen bg-surface-0 text-fg">
      <Providers>
        {children}
        <SiteFooter />
      </Providers>
    </div>
  );
}
