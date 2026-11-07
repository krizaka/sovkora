import type { Metadata } from "next";
import { headers } from "next/headers";
import localFont from "next/font/local";
import "./globals.css";
import "@krizaka/ui/blocks.css";
import { readAuthFlags } from "@/core/config/auth-flags";
import { readBrandName } from "@/core/brand/brand.server";
import { developmentDeployment, indexable } from "@/core/config/indexing";
import { PublicShell } from "@/components/layout/PublicShell";
import { ThemeScript } from "@krizaka/ui/theme";

import { cn } from "@krizaka/ui/cn";
import { siteJsonLd } from "@/app/_seo/seo";
import { siteMetadata } from "@/app/_seo/site-metadata";
import { LOCALE_HEADER, requestLocale } from "@/core/i18n/locale-negotiation";

/*
 * The typefaces are the files of @krizaka/fonts (SIL OFL 1.1), loaded by next/font/local: preloaded, fingerprinted and
 * served from the same origin — never fetched from Google at build. The app is a product, so it wears plain Krizaka UI
 * (`data-surface="product"` on <html>): the text sans, and Plus Jakarta Sans for the titles (ADR-006, mapped to
 * `--kz-font-product-display` in globals.css) — no Young Serif, no Courier Prime (those are the notebook's).
 * The variables sit on <html>, where the tokens read them (`--kz-font-sans` / `--kz-font-product-display` are resolved there:
 * on <body> they would be undefined and the text would fall back to the system face).
 * The arguments must be literals, relative to this file: scripts/link-fonts.mjs copies the faces to ./_fonts (git-ignored)
 * from wherever the package is installed (standalone on App Platform, hoisted in the workspace).
 */
const inter = localFont({
  src: "./_fonts/atkinson-hyperlegible-next-latin-wght.woff2",
  weight: "400 700",
  variable: "--font-inter",
  display: "swap",
});

const productDisplay = localFont({
  src: "./_fonts/plus-jakarta-sans-latin-wght.woff2",
  weight: "500 800",
  variable: "--font-product-display",
  display: "swap",
});

/** The title, description and Open Graph in the page's language (`/fr`, `/en`, else the browser's), closed to search engines when the deployment is. */
export async function generateMetadata(): Promise<Metadata> {
  const h = await headers();
  return siteMetadata(requestLocale(h.get(LOCALE_HEADER), h.get("accept-language")), indexable());
}

/**
 * Root layout component for the entire Next.js application.
 *
 * @param props The layout component properties.
 * @returns The HTML structure of the layout.
 */
export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const authFlags = readAuthFlags();
  const brand = await readBrandName(authFlags);
  return (
    <html
      lang="fr"
      data-surface="product"
      className={cn(inter.variable, productDisplay.variable)}
      suppressHydrationWarning
    >
      <head>
        {/* The organisation's theme mechanism: applies the persisted mode and named theme before the first paint. */}
        <ThemeScript />
        {/* Without JavaScript nothing rises into view: no [data-reveal] block may stay invisible. */}
        <noscript>
          <style>{"[data-reveal]{opacity:1!important;transform:none!important;filter:none!important}"}</style>
        </noscript>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(siteJsonLd()).replace(/</g, String.raw`\u003c`) }}
        />
      </head>
      <body className="antialiased transition-colors duration-200">
        <PublicShell authFlags={authFlags} brand={brand} showDevNotice={developmentDeployment()}>
          {children}
        </PublicShell>
      </body>
    </html>
  );
}
