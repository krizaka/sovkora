import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/**
 * The public export (krizaka-internal#4625): `scripts/build-public.mjs` stages a project per language that links the page files listed in
 * `public-build/routes.mjs` and this file as its `next.config.mjs`, then runs `next build` there. A static export has no server,
 * no proxy and no API: the pages are rendered once, here, and the result is plain files.
 */
const packageVersion = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf8")).version;
const appVersion = process.env.APP_VERSION?.trim() || packageVersion;
/** The language this build is written in (`scripts/build-public.mjs` builds one project per language): its base path, `/fr` or `/en`. */
const locale = process.env.NEXT_PUBLIC_LOCALE === "en" ? "en" : "fr";

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "export",
  // Each page is a folder with an index.html: any static host serves /fr/docs without a rewrite rule.
  trailingSlash: true,
  // One build per language, each under its own base path: every internal link takes the prefix (`/fr/docs`) with no code of ours.
  basePath: `/${locale}`,
  // The monorepo root: the linked files resolve outside the staged folder.
  // The modules that differ wholesale from the dev app are swapped here and nowhere else: the language (the build's, there is no
  // proxy), the editions on offer, the language switch, the recordings (see NoMedia.tsx); the one-line differences read `src/core/config/build-target.ts`.
  turbopack: {
    root: fileURLToPath(new URL("../..", import.meta.url)),
    resolveAlias: {
      "@/core/context/LocaleContext": "./public-build/LocaleContext.tsx",
      "@/components/LocaleSwitch": "./public-build/LocaleSwitch.tsx",
      "@/features/landing/components/PricingEditions": "./public-build/PricingEditions.tsx",
      "@/core/clips/Clip": "./public-build/NoMedia.tsx",
      "@/features/landing/components/StudioOutputs": "./public-build/NoMedia.tsx",
      "@/features/landing/components/LandingFilm": "./public-build/NoMedia.tsx",
      "@/features/landing/components/LandingRecords": "./public-build/NoMedia.tsx",
      "@/features/landing/components/PhoneSection": "./public-build/NoMedia.tsx",
    },
  },
  transpilePackages: [
    "@krizaka/orazaka-design-system",
    "@krizaka/orazaka-shared",
    "@krizaka/org",
    "@krizaka/ui",
    "@krizaka/query",
    "@krizaka/charts",
    "@krizaka/tokens",
    "@krizaka/icons",
    "@krizaka/intl",
    "@krizaka/pacer",
    "@tanstack/pacer",
    "@tanstack/react-table",
    "@tanstack/table-core",
    "@tanstack/react-virtual",
    "@tanstack/virtual-core",
    "@tanstack/store",
    "@tanstack/react-store",
    "@tanstack/markdown",
    "@tanstack/highlight",
    "@krizaka/settings",
    "@krizaka/db",
  ],
  reactStrictMode: true,
  poweredByHeader: false,
  images: { unoptimized: true },
  agentRules: false,
  env: {
    NEXT_PUBLIC_APP_VERSION: appVersion,
    NEXT_PUBLIC_APP_ENV: "production",
    NEXT_PUBLIC_LOCALE: locale,
    NEXT_PUBLIC_BUILD_TARGET: "public",
  },
};

export default nextConfig;
