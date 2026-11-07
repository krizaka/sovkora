/**
 * @file studios.ts
 * @description The words of the Studios console page and its usage table, en and fr (krizaka-internal#2093).
 */
import { localise, w, type Localised } from "@/core/context/words";

const WORDS = {
  page: {
    title: w("Studios", "Studios"),
    lead: w(
      "A Studio is data: the workflow, its steps and its forms are written here and published without a deploy.",
      "Un Studio est une donnée : le workflow, ses étapes et ses formulaires s’écrivent ici et se publient sans déploiement.",
    ),
    usage: w("Usage", "Utilisation"),
    builder: w("Editor", "Éditeur"),
  },
  analytics: {
    loading: w("Loading…", "Chargement…"),
    failed: w("Could not load", "Chargement impossible"),
    caption: w("Studio usage", "Usage des Studios"),
    reading: w("Reading the usage", "Lecture de l'usage"),
    empty: w("No Studio measured.", "Aucun Studio mesuré."),
    studio: w("Studio", "Studio"),
    installations: w("Installations", "Installations"),
    runs: w("Runs", "Exécutions"),
    failures: w("failed", "échecs"),
    successRate: w("Success", "Réussite"),
    credits: w("Credits / run", "Crédits / exéc."),
    unused: w("installed, never run", "installé, jamais lancé"),
    unusedHint: w(
      "Installed then never run: the clearest churn signal of the product",
      "Installé puis jamais lancé — le signal de churn le plus net du produit",
    ),
  },
} as const;

export type StudiosWords = Localised<typeof WORDS>;
export type StudiosLocale = "en" | "fr";
export const studiosCopy: Record<StudiosLocale, StudiosWords> = { en: localise(WORDS, 0), fr: localise(WORDS, 1) };
