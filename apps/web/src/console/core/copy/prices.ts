/**
 * @file prices.ts
 * @description The words of the price catalogue screen (krizaka/orazaka#417), en and fr. No amount here: the figures
 * are the catalogue's, read from billing (ADR-009).
 */
import { localise, w, type Localised } from "@/core/context/words";

const WORDS = {
  title: w("Price catalogue", "Catalogue de prix"),
  lead: w(
    "The price of every edition, kept by billing. A change starts from a date you choose and never touches what customers already hold.",
    "Le prix de chaque édition, tenu par la facturation. Un changement part d'une date que vous choisissez et ne touche jamais ce que les clients détiennent déjà.",
  ),
  error: w(
    "The catalogue could not be read: billing may be starting.",
    "Le catalogue n'a pas pu être lu : la facturation démarre peut-être.",
  ),
  retry: w("Retry", "Réessayer"),
  loading: w("Reading the catalogue", "Lecture du catalogue"),
  editor: {
    edit: w("Change", "Modifier"),
    amount: w("Amount", "Montant"),
    onQuote: w("On quote", "Sur devis"),
    plusTaxes: w("plus taxes", "plus taxes"),
    unit: w("Unit", "Unité"),
    availability: w("Availability", "Disponibilité"),
    limits: w("Limits", "Limites"),
    from: w("From", "À partir du"),
    keepsPrice: w("Current subscriptions keep their price.", "Les abonnements en cours gardent leur prix."),
    save: w("Schedule the change", "Programmer le changement"),
    cancel: w("Cancel", "Annuler"),
    emptyIsQuote: w("Leave empty for “on quote”.", "Laisser vide pour « sur devis »."),
    scheduled: w("Scheduled changes ({count})", "Changements programmés ({count})"),
    history: w("History", "Historique"),
    historyEmpty: w("No change yet.", "Aucun changement."),
    loading: w("Loading…", "Chargement…"),
    historyLine: w("{at}: {before} → {after}, from {from}", "{at} : {before} → {after}, à partir du {from}"),
    scheduledLine: w("from {from}", "à partir du {from}"),
    saved: w("Saved.", "Enregistré."),
    units: {
      month: w("per month", "par mois"),
      year: w("per year", "par an"),
      once: w("once", "une fois"),
      document: w("per document", "par document"),
      order: w("per order", "par commande"),
      pack: w("per pack", "par pack"),
      hour: w("per hour", "par heure"),
    },
    availabilities: {
      public: w("Public", "Public"),
      internal: w("Internal", "Interne"),
      "founder-limited": w("Founder-limited", "Limité aux fondateurs"),
      preparing: w("Preparing", "En préparation"),
    },
  },
} as const;

export type PricesWords = Localised<typeof WORDS>;
export type PricesLocale = "en" | "fr";
export const pricesCopy: Record<PricesLocale, PricesWords> = { en: localise(WORDS, 0), fr: localise(WORDS, 1) };
