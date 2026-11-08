"use client";

import Link from "next/link";
import { Button } from "@krizaka/ui/button";
import { PricingPage, type PricingPlan } from "@krizaka/ui/pricing-page";
import { useTranslation } from "@/core/context/LocaleContext";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { contactUrl } from "@/core/config/contact";
import { inDevelopment } from "@/core/config/product-status";
import { priceFor, type PublicPrice } from "@/features/landing/utils/public-prices";

const REPO_URL = "https://github.com/krizaka/orazaka";

/**
 * `/pricing` — the editions on @krizaka/ui's pricing template: Community (open source, what runs), Business (soon, no
 * figure) and Enterprise (Krizaka's single custom offer, through the contact form). The price slot holds an amount
 * only when the price catalogue (ADR-009, read on the server) publishes one for the edition; otherwise it holds the
 * edition's own words. Words: `t.pricing`.
 */
export function PricingEditions({ prices = [] }: Readonly<{ prices?: readonly PublicPrice[] }>) {
  const { t, locale } = useTranslation();
  const p = t.pricing;
  // Where the Enterprise offer is reached: Krizaka's contact form in the page's language, the Sovkora topic preselected (the offer has no page of its own yet).
  const ENTERPRISE_URL = contactUrl(locale);
  const slot = (edition: "community" | "business" | "enterprise") => {
    // While Sovkora is in development no amount is public: a price nobody can pay is never shown (krizaka-com#468).
    const shown = inDevelopment() ? undefined : priceFor(prices, edition, locale);
    return shown
      ? { price: shown.text, unit: `${p.units[shown.unit] ?? shown.unit} · ${p.plusTaxes}` }
      : { price: p[edition].price, unit: p[edition].unit };
  };

  const plans: PricingPlan[] = [
    {
      id: "community",
      name: p.community.name,
      ...slot("community"),
      features: p.community.features,
      featured: true,
      action: (
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="primary" size="md" shape="cut">
            <Link href="/desktop">{p.community.download}</Link>
          </Button>
          <Button asChild variant="secondary" size="md" shape="cut">
            <a href={REPO_URL} target="_blank" rel="noopener noreferrer">
              {p.community.source}
            </a>
          </Button>
        </div>
      ),
    },
    {
      id: "business",
      name: p.business.name,
      ...slot("business"),
      features: p.business.features,
      action: <p className="font-mono text-sm text-fg-secondary">{p.business.soon}</p>,
    },
    {
      id: "enterprise",
      name: p.enterprise.name,
      ...slot("enterprise"),
      features: p.enterprise.features,
      action: (
        <Button asChild variant="secondary" size="md" shape="cut">
          <a href={ENTERPRISE_URL}>{p.enterprise.cta}</a>
        </Button>
      ),
    },
  ];

  return (
    <>
      <LandingNav onHome={false} />
      <main id="content" className="min-h-screen bg-surface-0 text-fg">
        <PricingPage
          brand="orazaka"
          eyebrow={p.eyebrow}
          title={p.title}
          lead={p.lead}
          plansLabel={p.plansLabel}
          plans={plans}
          credits={{
            title: p.custom.title,
            children: (
              <>
                <p>{p.custom.lead}</p>
                <ul>
                  {p.custom.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
                <p>
                  <Button asChild variant="primary" size="md" shape="cut">
                    <a href={ENTERPRISE_URL}>{p.custom.cta}</a>
                  </Button>
                </p>
              </>
            ),
          }}
          faq={{ title: p.faq.title, items: p.faq.items.map((i) => ({ question: i.question, answer: i.answer })) }}
        />
      </main>
      <LandingFooter withLanguage />
    </>
  );
}
