/**
 * @file translations.pricing.ts
 * @description The words of /pricing (the editions of Sovkora), en and fr (krizaka/orazaka#447). Prices are data
 * (ADR-009): they come from the price catalogue (billing), never from here — an edition without a published price
 * carries none of its own. Sovkora is in development: nothing is for sale and no price is shown. Community is the open-source platform; Business is « soon »; anything custom is Krizaka's single
 * « Entreprise » offer, reached through the contact form. No customer name, no unproven claim (no certification, no
 * hosting or uptime promise).
 */
import { localise, w } from "./words";

type Locale = "en" | "fr";

interface Edition {
  name: string;
  /** In the price slot: what the edition is, never an amount. */
  price: string;
  unit: string;
  features: string[];
}

export interface PricingDictionary {
  meta: { title: string; description: string };
  eyebrow: string;
  title: string;
  lead: string;
  plansLabel: string;
  /** The words of the catalogue's units (`year`, `month`…), read next to a price the catalogue holds. */
  units: Record<string, string>;
  plusTaxes: string;
  community: Edition & { download: string; source: string };
  business: Edition & { soon: string };
  enterprise: Edition & { cta: string };
  custom: { title: string; lead: string; items: string[]; cta: string };
  faq: { title: string; items: { question: string; answer: string }[] };
}

const WORDS = {
  meta: {
    title: w("Pricing and editions — {brand}", "Tarifs et éditions — {brand}"),
    description: w(
      "{brand}'s editions: Community, the open-source platform you run on your own machines; Business, coming soon; and Krizaka's Enterprise offer for anything custom.",
      "Les éditions de {brand} : Community, la plateforme libre que vous faites tourner sur vos machines ; Business, bientôt ; et l'offre Entreprise de Krizaka pour tout sur mesure.",
    ),
  },
  eyebrow: w("Pricing and editions", "Tarifs et éditions"),
  title: w("Start with Community. Talk to us for the rest.", "Commencez avec Community. Parlons-en pour le reste."),
  lead: w(
    "{brand} is open source and in development: sign-up is closed and nothing is for sale yet. Community is the platform itself; no edition shows a price until it is published.",
    "{brand} est libre et en développement : les inscriptions sont fermées et rien n'est encore en vente. Community est la plateforme elle-même ; aucune édition n'affiche de prix tant qu'il n'est pas publié.",
  ),
  plansLabel: w("Editions", "Éditions"),
  units: {
    month: w("per month", "par mois"),
    year: w("per year", "par an"),
    once: w("once", "une fois"),
    document: w("per document", "par document"),
    order: w("per order", "par commande"),
    pack: w("per pack", "par pack"),
    hour: w("per hour", "par heure"),
  },
  plusTaxes: w("plus taxes", "plus taxes"),
  community: {
    name: w("Community", "Community"),
    price: w("Open source", "Libre"),
    unit: w("Apache-2.0 · run it yourself", "Apache-2.0 · à héberger vous-même"),
    features: [
      w(
        "The platform: chat, documents, images, video and agents.",
        "La plateforme : clavardage, documents, images, vidéo et agents.",
      ),
      w("Runs on your own machines, with local models.", "Tourne sur vos propres machines, avec des modèles locaux."),
      w("The source is public, and so is the documentation.", "Le code source est public, la documentation aussi."),
      w(
        "Provider keys you add are billed by those providers, not by {brand}.",
        "Les clés de fournisseurs que vous ajoutez sont facturées par ces fournisseurs, pas par {brand}.",
      ),
    ],
    download: w("{brand} for Mac", "{brand} pour Mac"),
    source: w("See the source", "Voir le code source"),
  },
  business: {
    name: w("Business", "Business"),
    price: w("Soon", "Bientôt"),
    unit: w("Not published yet", "Pas encore publiée"),
    features: [
      w("Planned for teams that run {brand} together.", "Prévue pour les équipes qui utilisent {brand} ensemble."),
      w("No date and no price until it is published.", "Ni date ni prix tant qu'elle n'est pas publiée."),
    ],
    soon: w("Soon", "Bientôt"),
  },
  enterprise: {
    name: w("Enterprise", "Entreprise"),
    price: w("On request", "Sur demande"),
    unit: w("Custom, with Krizaka", "Sur mesure, avec Krizaka"),
    features: [
      w("Custom modules and integrations on {brand}.", "Modules et intégrations sur mesure sur {brand}."),
      w("Sovereign AI adoption, on your premises.", "Adoption d'une IA souveraine, chez vous."),
      w("Maintenance and support of what Krizaka delivers.", "Maintenance et soutien de ce que Krizaka livre."),
    ],
    cta: w("Talk about your project", "Parler de votre projet"),
  },
  custom: {
    title: w("Something custom?", "Un besoin sur mesure ?"),
    lead: w(
      "Krizaka has one Enterprise offer, with three parts. Anything beyond Community goes through it.",
      "Krizaka a une seule offre Entreprise, en trois volets. Tout ce qui dépasse Community passe par elle.",
    ),
    items: [
      w("Evolutions and custom modules for your business.", "Évolutions et modules sur mesure pour votre entreprise."),
      w("Sovereign AI adoption on {brand}.", "Adoption d'une IA souveraine sur {brand}."),
      w("Maintenance and support of what we deliver.", "Maintenance et soutien de ce que nous livrons."),
    ],
    cta: w("Talk about your project", "Parler de votre projet"),
  },
  faq: {
    title: w("Questions", "Questions"),
    items: [
      {
        question: w("Is Community limited?", "Community est-elle limitée ?"),
        answer: w(
          "No. It is the same platform as the repository, under its open-source licence. What you run is yours to run, change and host.",
          "Non. C'est la même plateforme que le dépôt, sous sa licence libre. Ce que vous faites tourner, vous le gérez, le modifiez et l'hébergez.",
        ),
      },
      {
        question: w("Where are the prices?", "Où sont les prix ?"),
        answer: w(
          "{brand} is in development, so no edition has a price yet. Each edition says « Soon » or « On request » until a price is published.",
          "{brand} est en développement : aucune édition n'a encore de prix. Chaque édition dit « Bientôt » ou « Sur demande » tant qu'un prix n'est pas publié.",
        ),
      },
      {
        question: w("When does Business arrive?", "Quand arrive Business ?"),
        answer: w(
          "It is announced as soon, with no date. This page changes the day it is published.",
          "Elle est annoncée comme bientôt, sans date. Cette page change le jour de sa publication.",
        ),
      },
      {
        question: w("How do I ask for something custom?", "Comment demander du sur mesure ?"),
        answer: w(
          "Through the contact form on krizaka.com: describe your project and we answer within business hours.",
          "Par le formulaire de contact de krizaka.com : décrivez votre projet et nous répondons aux heures d'affaires.",
        ),
      },
    ],
  },
} as const;

export const pricing: Record<Locale, PricingDictionary> = {
  en: localise(WORDS, 0),
  fr: localise(WORDS, 1),
};
