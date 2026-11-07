/* eslint-disable @next/next/no-img-element */
"use client";

import { LandingFooter } from "./LandingFooter";
import Link from "next/link";
import { useTranslation } from "@/core/context/LocaleContext";
import type { Locale } from "@/core/context/translations.types";

export type LegalContent = {
  title: string;
  subtitle: string;
  lastUpdated: string;
  intro: string;
  sections: { title: string; text: string }[];
  backToLogin: string;
};

/** The page of a legal text (privacy, terms): header, the card, the sections in two columns. The texts stay in each route's CONTENT. */
export function LegalPage({ content }: { content: Record<Locale, LegalContent> }) {
  const { locale } = useTranslation();
  const c = content[locale] || content.en;

  return (
    <>
      <main className="min-h-screen flex flex-col items-center p-6 bg-surface-0 ambient-grid w-full overflow-y-auto">
        {/* Navbar header */}
        <header className="w-full max-w-4xl flex items-center justify-between py-4 mb-6 border-b border-border-subtle">
          <Link href="/login" className="flex items-center gap-2 hover:opacity-80 transition-opacity">
            <img src="/logo.svg" alt="Sovkora Logo" width={24} height={24} className="w-6 h-6" />
            <span className="text-lg font-bold tracking-tight text-fg">Sovkora</span>
          </Link>

          <Link
            href="/login"
            className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-border-default hover:border-accent transition-colors hover:text-accent text-fg-secondary"
          >
            {c.backToLogin}
          </Link>
        </header>

        {/* Main Glass Card container */}
        <article className="w-full max-w-4xl glass-card rounded-2xl p-6 md:p-10 space-y-8 animate-fade-up shadow-2xl">
          <header className="space-y-2 border-b border-border-subtle pb-6">
            <h1 className="text-3xl font-extrabold tracking-tight text-fg md:text-4xl">{c.title}</h1>
            <p className="text-accent font-medium text-sm md:text-base">{c.subtitle}</p>
            <p className="text-xs text-fg-muted pt-1">{c.lastUpdated}</p>
          </header>

          <p className="text-fg-secondary text-sm md:text-base leading-relaxed">{c.intro}</p>

          <div className="grid gap-6 md:grid-cols-2 pt-2">
            {c.sections.map((section, idx) => (
              <section
                key={idx}
                className="p-5 rounded-xl border border-border-subtle bg-surface-1 hover:border-border-strong transition-colors duration-200"
              >
                <h2 className="text-base font-bold text-fg mb-2">{section.title}</h2>
                <p className="text-xs text-fg-secondary leading-relaxed">{section.text}</p>
              </section>
            ))}
          </div>
        </article>
      </main>
      <LandingFooter withLanguage />
    </>
  );
}
