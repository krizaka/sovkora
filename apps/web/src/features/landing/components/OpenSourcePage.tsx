"use client";

import Link from "next/link";
import { ExternalIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";
import { CardGrid } from "@krizaka/ui/card-grid";
import { LinkCard } from "@krizaka/ui/link-card";
import { Section } from "@krizaka/ui/page";
import { PageHero } from "@krizaka/ui/page-hero";
import { useTranslation } from "@/core/context/LocaleContext";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { ORG_URL, type ListedRepo } from "@/features/landing/utils/repositories";

export const REPO_URL = "https://github.com/krizaka/orazaka";
export const SECURITY_URL = `${REPO_URL}/security/policy`;
export const LICENSING_URL = `${REPO_URL}/blob/main/docs/LICENSING.md`;

/**
 * `/open-source` (docs-hub template): the repositories (read at build), the licence line, self-hosting in three lines and how to
 * contribute. Words: `t.openSource`. Every claim restates the Sovkora repository's LICENSING and SELF_HOSTING pages.
 */
export function OpenSourcePage({ repos }: Readonly<{ repos: readonly ListedRepo[] | null }>) {
  const { t, locale } = useTranslation();
  const o = t.openSource;
  const date = (iso: string | null) =>
    iso ? new Intl.DateTimeFormat(locale, { dateStyle: "medium" }).format(new Date(iso)) : "";

  return (
    <>
      <LandingNav onHome={false} />
      <main id="content" className="min-h-screen bg-surface-0 text-fg">
        <PageHero eyebrow={o.eyebrow} title={o.title} lead={o.lead}>
          <div className="flex flex-wrap gap-3">
            <Button asChild variant="primary" size="lg" shape="cut">
              <a href={REPO_URL} target="_blank" rel="noopener noreferrer">
                {o.source}
                <ExternalIcon size={16} />
              </a>
            </Button>
            <Button asChild variant="secondary" size="lg" shape="cut">
              <Link href="/docs">{o.docs}</Link>
            </Button>
          </div>
        </PageHero>

        <Section id="repositories" title={o.repos.title} lead={o.repos.lead} divider>
          {repos ? (
            <CardGrid as="ul" aria-label={o.repos.label} min={280}>
              {repos.map((r) => (
                <LinkCard
                  key={r.name}
                  href={r.url}
                  title={r.name}
                  titleAs="h3"
                  description={r.description}
                  meta={r.pushedAt ? `${o.repos.updated} · ${date(r.pushedAt)}` : undefined}
                  cta={o.source}
                  target="_blank"
                  rel="noopener noreferrer"
                />
              ))}
            </CardGrid>
          ) : (
            <p className="text-fg-secondary">
              {o.repos.unavailable}{" "}
              <a className="text-fg-accent underline" href={ORG_URL} target="_blank" rel="noopener noreferrer">
                {o.repos.all}
              </a>
            </p>
          )}
        </Section>

        <Section id="licence" title={o.licence.title} lead={o.licence.lead} divider>
          <ul aria-label={o.licence.label} className="grid gap-3 md:grid-cols-3">
            {o.licence.rows.map((row) => (
              <li key={row.layer} className="rounded-lg border border-border-subtle bg-surface-1 p-4">
                <p className="font-medium text-fg">{row.layer}</p>
                <p className="mt-2 text-sm text-fg-secondary">
                  {o.licence.where} · {row.where}
                </p>
                <p className="font-mono text-sm text-fg-accent">{row.licence}</p>
              </li>
            ))}
          </ul>
          <p className="mt-4 text-sm text-fg-secondary">
            <a className="text-fg-accent underline" href={LICENSING_URL} target="_blank" rel="noopener noreferrer">
              {o.licence.note}
            </a>
          </p>
        </Section>

        <Section id="self-hosting" title={o.selfHost.title} lead={o.selfHost.lead} divider>
          <ol className="list-decimal space-y-1 pl-5 text-fg-secondary">
            {o.selfHost.steps.map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ol>
          <p className="mt-4">
            <Button asChild variant="secondary" size="md" shape="cut">
              <Link href="/docs/self-hosting">{o.selfHost.cta}</Link>
            </Button>
          </p>
        </Section>

        <Section id="contribute" title={o.contribute.title} lead={o.contribute.lead} divider>
          <CardGrid as="ul" min={240}>
            {o.contribute.items.map((i) => (
              <div key={i.title} className="rounded-lg border border-border-subtle bg-surface-1 p-4">
                <h3 className="font-medium text-fg">{i.title}</h3>
                <p className="mt-1 text-sm text-fg-secondary">{i.text}</p>
              </div>
            ))}
          </CardGrid>
          <p className="mt-4">
            <Button asChild variant="secondary" size="md" shape="cut">
              <a href={SECURITY_URL} target="_blank" rel="noopener noreferrer">
                {o.contribute.cta}
              </a>
            </Button>
          </p>
        </Section>
      </main>
      <LandingFooter withLanguage />
    </>
  );
}
