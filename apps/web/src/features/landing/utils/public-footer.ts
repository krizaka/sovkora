import { org } from "@krizaka/org";
import type { FooterStatus, SiteFooterConfig } from "@krizaka/ui/site-footer";
import { z } from "zod";
import data from "@/../content/public-footer.json";
import { PUBLIC_PAGES } from "@/../scripts/public-routes.mjs";
import { contactUrl } from "@/core/config/contact";
import type { LandingDictionary } from "@/core/context/translations.landing.types";
import type { SiteLocale } from "@/core/i18n/locale-negotiation";

type FooterWords = LandingDictionary["footer"];
type WordKey = keyof FooterWords;

function required<T>(value: T | undefined, what: string): T {
  if (value === undefined) throw new Error(`${what} is not in @krizaka/org`);
  return value;
}
const PRODUCT = required(
  org.products.find((p) => p.id === "sovkora"),
  "the product sovkora",
);
const PUBLISHER = required(
  org.sites.find((s) => s.id === "krizaka"),
  "the site krizaka",
);

/** The statuses a footer column says (@krizaka/ui): a product status outside them is not said. */
const FOOTER_STATUSES: readonly string[] = [
  "upcoming",
  "construction",
  "development",
] satisfies readonly FooterStatus[];
const isFooterStatus = (status: string): status is FooterStatus => FOOTER_STATUSES.includes(status);

/** The site's own addresses a footer link may name, all read from @krizaka/org or the contact config: never a typed URL in the data. */
export const SiteAddress = { Repo: "repo", Licences: "licences", Contact: "contact", Publisher: "publisher" } as const;
export type SiteAddress = (typeof SiteAddress)[keyof typeof SiteAddress];

const siteHref = (site: SiteAddress, locale: SiteLocale): string => {
  switch (site) {
    case SiteAddress.Repo:
      return PRODUCT.repo;
    // NOTICE says which licence covers which folder (FSL-1.1-ALv2 for the platform, Apache-2.0 for the CLI, contracts, edge and packs).
    case SiteAddress.Licences:
      return `${PRODUCT.repo}/blob/main/NOTICE`;
    case SiteAddress.Contact:
      return contactUrl(locale);
    case SiteAddress.Publisher:
      return `${PUBLISHER.url}/${locale}`;
  }
};

/** A key of `t.landing.footer`, checked against the reference words: unknown words refuse the data. */
const wordKey = (words: FooterWords) =>
  z.custom<WordKey>((key) => typeof key === "string" && typeof Reflect.get(words, key) === "string", {
    message: "not a key of t.landing.footer",
  });

const schema = (words: FooterWords) => {
  const key = wordKey(words);
  const link = z.union([
    z.object({ labelKey: key, page: z.string() }),
    z.object({ labelKey: key, site: z.enum(Object.values(SiteAddress) as [SiteAddress, ...SiteAddress[]]) }),
  ]);
  return z.object({
    labelKey: key,
    columns: z.array(z.object({ id: z.string(), titleKey: key, status: z.boolean().optional(), links: z.array(link) })),
    legal: z.object({ showVersion: z.boolean(), links: z.array(link) }),
    social: z.array(z.string()),
  });
};
export type PublicFooterData = z.infer<ReturnType<typeof schema>>;
type Link = PublicFooterData["legal"]["links"][number];
type FooterLink = SiteFooterConfig["columns"][number]["links"][number];

/**
 * The footer of the public export, in one language: the columns with only the links to pages the export holds (no download,
 * no terms, no sign-in), the product's status said once on its column, then one legal line — the publisher's legal name, NEQ
 * and city from @krizaka/org, never typed — without a version, and the org's socials.
 */
export function publicFooter(
  locale: SiteLocale,
  words: FooterWords,
  data_: unknown = data,
  pages: readonly string[] = PUBLIC_PAGES,
): SiteFooterConfig {
  const footer = schema(words).parse(data_);
  const shown = (l: Link): boolean => !("page" in l) || pages.includes(l.page);
  const toLink = (l: Link): FooterLink =>
    "page" in l
      ? { label: words[l.labelKey], href: `/${l.page}` }
      : { label: words[l.labelKey], href: siteHref(l.site, locale), external: true };
  const status = isFooterStatus(PRODUCT.status) ? PRODUCT.status : undefined;
  const { company } = org;
  return {
    label: words[footer.labelKey],
    columns: footer.columns
      .map((c) => ({
        id: c.id,
        title: words[c.titleKey],
        ...(c.status && status ? { status } : {}),
        links: c.links.filter(shown).map(toLink),
      }))
      .filter((c) => c.links.length > 0),
    legal: {
      legalName: company.legalName,
      neq: company.neq,
      city: company.city,
      showVersion: footer.legal.showVersion,
      links: footer.legal.links.filter(shown).map(toLink),
    },
    social: org.links.social
      .filter((s) => footer.social.includes(s.id))
      .map((s) => ({ id: s.id, label: s.label, href: s.url })),
  };
}
