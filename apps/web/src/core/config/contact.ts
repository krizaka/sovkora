import type { SiteLocale } from "@/core/i18n/locale-negotiation";

/** Krizaka's one contact form, in the page's language, with the product and the site it was reached from (the same links as the other product sites). */
export const contactUrl = (locale: SiteLocale): string =>
  `https://www.krizaka.com/${locale}/contact?topic=sovkora&source=sovkora-app`;
