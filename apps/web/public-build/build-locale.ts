import type { SiteLocale } from "@/core/i18n/locale-negotiation";

/**
 * The language this build of the public export is written in. The export is built once per language, each under its own base path
 * (`/fr`, `/en`: `next.config.public.mjs`), so every internal link carries the prefix without a line of ours and the static HTML of a
 * page is already in its language. The language is read at build time, never from a request.
 */
export const BUILD_LOCALE: SiteLocale = process.env.NEXT_PUBLIC_LOCALE === "en" ? "en" : "fr";
