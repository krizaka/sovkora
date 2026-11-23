"use client";

import { Button } from "@krizaka/ui/button";
import { PricingPage, type PricingPlan } from "@krizaka/ui/pricing-page";
import { useTranslation } from "@/core/context/LocaleContext";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { contactUrl } from "@/core/config/contact";

const REPO_URL = "https://github.com/krizaka/sovkora";

const SOON = /\b(?:soon|bientôt)\b/i;

/** The questions that do not promise an edition for later: an offer is sold or absent. */
const offered = (items: readonly { question: string; answer: string }[]) =>
  items
    .filter((i) => !SOON.test(i.question) && !SOON.test(i.answer))
    .map((i) => ({ question: i.question, answer: i.answer }));

/**
 * `/pricing` of the public export (an alias of `next.config.public.mjs`): what is on offer today — Community (open source, what runs) and
 * Enterprise (the single custom offer, through the contact form). The Business edition has no figure and is not on sale yet: an
 * offer is sold or absent, never « soon ». No amount (the product is in development), no link to a page that is not exported.
 */
export function PricingEditions() {
  const { t, locale } = useTranslation();
  const p = t.pricing;
  const contact = contactUrl(locale);
  return (
    <>
      <LandingNav onHome={false} />
      <main id="content" className="min-h-screen bg-surface-0 text-fg">
        <PricingPage
          brand="sovkora"
          eyebrow={p.eyebrow}
          title={p.title}
          lead={p.lead}
          plansLabel={p.plansLabel}
          plans={plansOf(p, contact)}
          credits={{ title: p.custom.title, children: <CustomOffer p={p} contact={contact} /> }}
          faq={{ title: p.faq.title, items: offered(p.faq.items) }}
        />
      </main>
      <LandingFooter withLanguage />
    </>
  );
}

type Words = ReturnType<typeof useTranslation>["t"]["pricing"];

/** Community and Enterprise: no amount, no link to a page that is not exported. */
function plansOf(p: Words, contact: string): PricingPlan[] {
  return [
    {
      id: "community",
      name: p.community.name,
      price: p.community.price,
      unit: p.community.unit,
      features: p.community.features,
      featured: true,
      action: (
        <Button asChild variant="secondary" size="md" shape="cut">
          <a href={REPO_URL} target="_blank" rel="noopener noreferrer">
            {p.community.source}
          </a>
        </Button>
      ),
    },
    {
      id: "enterprise",
      name: p.enterprise.name,
      price: p.enterprise.price,
      unit: p.enterprise.unit,
      features: p.enterprise.features,
      action: (
        <Button asChild variant="secondary" size="md" shape="cut">
          <a href={contact}>{p.enterprise.cta}</a>
        </Button>
      ),
    },
  ];
}

function CustomOffer({ p, contact }: Readonly<{ p: Words; contact: string }>) {
  return (
    <>
      <p>{p.custom.lead}</p>
      <ul>
        {p.custom.items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <p>
        <Button asChild variant="primary" size="md" shape="cut">
          <a href={contact}>{p.custom.cta}</a>
        </Button>
      </p>
    </>
  );
}
