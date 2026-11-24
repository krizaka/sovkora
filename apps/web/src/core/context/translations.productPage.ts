/**
 * @file translations.productPage.ts
 * @description The words of the public product page (the template of @krizaka/ui, `content/product-page.json`), each line with its
 * English and French side by side. Every claim is one the product's own copy already makes (`translations.landing.ts`): nothing is
 * promised that it does not do, no date, vouvoiement in French. The hero's title is the home's own.
 */
import type { Locale } from "./translations.types";
import { type Localised, localise, w } from "./words";

const WORDS = {
  hero: {
    title: w("An AI platform that runs on your machines.", "Une plateforme d'IA qui tourne sur vos machines."),
    lead: w(
      "Chat, documents, images and video, with the models you choose.",
      "Clavardage, documents, images et vidéo, avec les modèles que vous choisissez.",
    ),
    primary: w("Read the documentation", "Lire la documentation"),
    secondary: w("See it run", "Le voir tourner"),
  },
  scenes: {
    "phone-studio": {
      title: w("A sentence in, an image out", "Une phrase, une image"),
      line: w(
        "You type a sentence and follow the run, then open the image.",
        "Vous tapez une phrase, suivez l'exécution, puis ouvrez l'image.",
      ),
    },
    "phone-jobs": {
      title: w("Every result keeps its run", "Chaque résultat garde son exécution"),
      line: w("The jobs list shows the state of each one.", "La liste des tâches montre l'état de chacune."),
    },
  },
  limits: {
    title: w("What it does not do", "Ce qu'il ne fait pas"),
    items: {
      hosted: w("A hosted model is used only if you connect it.", "Un modèle hébergé ne sert que si vous le branchez."),
      agent: w("An agent acts only once you approve its plan.", "Un agent n'agit qu'une fois son plan approuvé."),
      accounts: w(
        "Accounts are not open and nothing is for sale.",
        "Les comptes ne sont pas ouverts et rien n'est en vente.",
      ),
    },
  },
  closing: {
    title: w("See what runs today.", "Voyez ce qui tourne aujourd'hui."),
  },
  media: {
    play: w("Play", "Lire"),
    pause: w("Pause", "Pause"),
  },
} as const;

export type ProductPageWords = Localised<typeof WORDS>;

/** The words of the page in a language. */
export const productPageWords = (locale: Locale): ProductPageWords => localise(WORDS, locale === "fr" ? 1 : 0);
