import type { Metadata } from "next";
import {
  KRIZAKA_URL,
  SITE_DESCRIPTION,
  SITE_NAME,
  SITE_TITLE,
  SITE_TITLE_FR,
  SITE_DESCRIPTION_FR,
  SITE_URL,
} from "@/app/_seo/seo";
import type { SiteLocale } from "@/core/i18n/locale-negotiation";

const OG_LOCALE = { fr: "fr_CA", en: "en_US" } as const;

/**
 * The site's metadata in the page's language (the `<title>`, Open Graph and Twitter card follow `/fr` and `/en`), closed to
 * search engines when the deployment is (`indexable()`: dev, preview and the code-name host).
 */
export function siteMetadata(locale: SiteLocale, open: boolean): Metadata {
  const title = locale === "fr" ? SITE_TITLE_FR : SITE_TITLE;
  const description = locale === "fr" ? SITE_DESCRIPTION_FR : SITE_DESCRIPTION;
  const other = locale === "fr" ? "en" : "fr";
  return {
    ...(open ? {} : { robots: { index: false, follow: false, noarchive: true } }),
    metadataBase: new URL(SITE_URL),
    title,
    description,
    applicationName: SITE_NAME,
    authors: [{ name: "Krizaka", url: KRIZAKA_URL }],
    // Every page is canonical to itself; the image is app/opengraph-image.tsx.
    alternates: { canonical: "./" },
    openGraph: {
      type: "website",
      siteName: SITE_NAME,
      title,
      description,
      locale: OG_LOCALE[locale],
      alternateLocale: OG_LOCALE[other],
    },
    twitter: { card: "summary_large_image", title, description },
    icons: {
      icon: [
        { url: "/favicon.svg", type: "image/svg+xml" },
        { url: "/favicon-32.png", sizes: "32x32", type: "image/png" },
      ],
      shortcut: "/favicon.svg",
      apple: "/apple-touch-icon.png",
    },
    manifest: "/manifest.webmanifest",
  };
}
