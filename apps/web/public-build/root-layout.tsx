import type { Metadata } from "next";
import localFont from "next/font/local";
import "@/app/globals.css";
import "@krizaka/ui/blocks.css";
import { ThemeScript } from "@krizaka/ui/theme";
import { cn } from "@krizaka/ui/cn";
import { siteJsonLd, WWW_URL } from "@/app/_seo/seo";
import { siteMetadata } from "@/app/_seo/site-metadata";
import { BUILD_LOCALE } from "@public-build/build-locale";
import { PublicProviders } from "@public-build/Providers";

/* The same faces as the dev app's root layout (files of @krizaka/fonts copied by scripts/link-fonts.mjs), loaded by next/font/local. */
const inter = localFont({
  src: "../src/app/_fonts/atkinson-hyperlegible-next-latin-wght.woff2",
  weight: "400 700",
  variable: "--font-inter",
  display: "swap",
});
const productDisplay = localFont({
  src: "../src/app/_fonts/plus-jakarta-sans-latin-wght.woff2",
  weight: "500 800",
  variable: "--font-product-display",
  display: "swap",
});

/** The title, description and Open Graph in the build's language; the export is the public site, so it is open to search engines. */
export const metadata: Metadata = siteMetadata(BUILD_LOCALE, true);

/** The root layout of the export: the dev app's (`app/layout.tsx`) without the request's headers, the session or the backend. */
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang={BUILD_LOCALE}
      data-surface="product"
      className={cn(inter.variable, productDisplay.variable)}
      suppressHydrationWarning
    >
      <head>
        <ThemeScript />
        <noscript>
          <style>{"[data-reveal]{opacity:1!important;transform:none!important;filter:none!important}"}</style>
        </noscript>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(siteJsonLd(WWW_URL)).replace(/</g, String.raw`<`) }}
        />
      </head>
      <body className="antialiased transition-colors duration-200">
        <PublicProviders>{children}</PublicProviders>
      </body>
    </html>
  );
}
