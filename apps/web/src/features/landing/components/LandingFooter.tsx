"use client";

import Link from "next/link";
import { SiteFooter } from "@krizaka/ui/site-footer";
import { familyLabels } from "@krizaka/ui/sites";
import { LocaleSwitch } from "@/components/LocaleSwitch";
import { useTranslation } from "@/core/context/LocaleContext";
import { contactUrl } from "@/core/config/contact";
import { APP_ENV, APP_VERSION } from "@/core/config/version";
import { NAV_DOCS, NAV_GROUPS, entryHref } from "@/features/landing/utils/site-nav";

/**
 * The footer of the public pages (NAV-SYS) is @krizaka/ui's SiteFooter: the bar's groups as columns (`site-nav.ts`:
 * Product, Docs), no family columns (a deployed product is independent), the publisher line « A Krizaka product », DEV · the version, Montréal. The legal pages
 * and the licence end the signature line. `withLanguage`: the pages that have no bar (privacy, terms, contact) carry the
 * language switch here.
 */
export function LandingFooter({ withLanguage = false }: Readonly<{ withLanguage?: boolean }>) {
  const { t, locale } = useTranslation();
  const f = t.landing.footer;

  return (
    <SiteFooter
      current="orazaka"
      family={false}
      labels={familyLabels[locale]}
      aria-label={f.product}
      version={APP_VERSION}
      env={APP_ENV === "production" ? undefined : APP_ENV}
      data-testid="app-version"
      legal={
        <>
          <span>{f.license}</span>
          <SiteFooter.Link asChild>
            <Link href="/privacy">{f.privacy}</Link>
          </SiteFooter.Link>
          <SiteFooter.Link asChild>
            <Link href="/terms">{f.terms}</Link>
          </SiteFooter.Link>
          <SiteFooter.Link asChild>
            <Link href="/pricing">{f.pricing}</Link>
          </SiteFooter.Link>
          <SiteFooter.Link asChild>
            <Link href="/open-source">{f.openSource}</Link>
          </SiteFooter.Link>
          <SiteFooter.Link asChild>
            <a href={contactUrl(locale)}>{f.contact}</a>
          </SiteFooter.Link>
          {withLanguage && <LocaleSwitch variant="inline" />}
        </>
      }
    >
      {NAV_GROUPS.map((g) => (
        <SiteFooter.Column key={g.id} title={t.landing.nav[g.id]}>
          {g.entries.map((e) => (
            <SiteFooter.Link key={e.id} asChild>
              <Link href={entryHref(e, false)}>{t.landing.nav[e.id]}</Link>
            </SiteFooter.Link>
          ))}
        </SiteFooter.Column>
      ))}
      <SiteFooter.Column title={t.landing.nav.docs}>
        <SiteFooter.Link asChild>
          <Link href={NAV_DOCS.path}>{f.docs}</Link>
        </SiteFooter.Link>
        <SiteFooter.Link asChild>
          <Link href="/docs/api">{f.api}</Link>
        </SiteFooter.Link>
        <SiteFooter.Link asChild>
          <Link href="/docs/cli">{f.cli}</Link>
        </SiteFooter.Link>
      </SiteFooter.Column>
    </SiteFooter>
  );
}
