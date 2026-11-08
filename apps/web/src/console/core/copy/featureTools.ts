/**
 * @file featureTools.ts
 * @description The words of the « Tool rounds per feature » screen, in English and French.
 */
import { localise, w, type Localised } from "@/core/context/words";

const WORDS = {
  title: w("Tool rounds per feature", "Tours d'outils par fonction"),
  lead: w(
    "How many times the model may call a tool in one turn, per feature. The default row applies to every feature without its own. A change takes effect on the next turn.",
    "Combien de fois le modèle peut appeler un outil dans un tour, par fonction. La ligne par défaut vaut pour toute fonction sans ligne propre. Un changement prend effet au tour suivant.",
  ),
  back: w("Engine settings", "Réglages du moteur"),
  link: w("Tool rounds per feature", "Tours d'outils par fonction"),
  error: w(
    "The bounds could not be read: the conversation service may be starting.",
    "Les bornes n'ont pas pu être lues : le service de conversation démarre peut-être.",
  ),
  retry: w("Retry", "Réessayer"),
  loading: w("Reading the bounds", "Lecture des bornes"),
  boundsTitle: w("Bounds", "Bornes"),
  defaultRow: w("Default for every feature", "Défaut de toutes les fonctions"),
  rounds: w("Rounds", "Tours"),
  range: w("{min} to {max} rounds", "de {min} à {max} tours"),
  save: w("Save", "Enregistrer"),
  saving: w("Saving", "Enregistrement"),
  saved: w("Saved — applies on the next turn.", "Enregistré — pris en compte au tour suivant."),
  remove: w("Use the default", "Revenir au défaut"),
  addTitle: w("Bound a feature", "Borner une fonction"),
  featureKey: w("Feature key", "Clé de la fonction"),
  featureKeyHint: w("For example agent.code.issue-to-pr", "Par exemple agent.code.issue-to-pr"),
  add: w("Add the bound", "Ajouter la borne"),
  invalidRange: w("Enter a whole number from {min} to {max}.", "Saisissez un nombre entier de {min} à {max}."),
  invalidKey: w("Enter the feature key.", "Saisissez la clé de la fonction."),
  failed: w("The change was refused.", "Le changement a été refusé."),
  structuredTitle: w("Answers per structured call", "Réponses par appel structuré"),
  structuredLead: w(
    "How many answers a structured-output call may ask the model for, per feature: the first, then the corrections that name what the schema refused. Out of answers, the call fails with an error, never a default value. A change takes effect on the next call.",
    "Combien de réponses un appel à sortie structurée peut demander au modèle, par fonction : la première, puis les corrections qui nomment ce que le schéma a refusé. Sans réponse valide, l'appel échoue avec une erreur, jamais une valeur par défaut. Un changement prend effet au prochain appel.",
  ),
  structuredDefaultRow: w("Default for structured answers", "Défaut des réponses structurées"),
  answers: w("Answers", "Réponses"),
  answersRange: w("{min} to {max} answers", "de {min} à {max} réponses"),
  invalidAnswers: w(
    "Enter a whole number of answers from {min} to {max}.",
    "Saisissez un nombre entier de réponses de {min} à {max}.",
  ),
  savedAnswers: w("Saved — applies on the next call.", "Enregistré — pris en compte au prochain appel."),
  structuredAddTitle: w("Bound a feature's structured calls", "Borner les appels structurés d'une fonction"),
  structuredFeatureKey: w("Feature key (structured calls)", "Clé de la fonction (appels structurés)"),
  addStructured: w("Add the structured bound", "Ajouter la borne structurée"),
  cacheTitle: w("Prompt cache per feature", "Cache du prompt par fonction"),
  cacheLead: w(
    "What of a feature's prompt a provider may cache to cut the cost of repeated turns. A sensitive turn is never cached, whatever the row says. A change takes effect on the next turn.",
    "Ce que le fournisseur peut mettre en cache du prompt d'une fonction pour réduire le coût des tours répétés. Un tour sensible n'est jamais mis en cache, quelle que soit la ligne. Un changement prend effet au tour suivant.",
  ),
  cacheDefaultRow: w("Default for every feature", "Défaut de toutes les fonctions"),
  cacheStrategy: w("What may be cached", "Ce qui peut être mis en cache"),
  cacheNONE: w("Nothing", "Rien"),
  cacheSYSTEM_ONLY: w("The system prompt", "Le prompt système"),
  cacheSYSTEM_AND_TOOLS: w("The system prompt and the tools", "Le prompt système et les outils"),
  cacheAddTitle: w("Set a feature's cache", "Régler le cache d'une fonction"),
  cacheAdd: w("Add the policy", "Ajouter la politique"),
  cacheChoose: w("Choose what may be cached.", "Choisissez ce qui peut être mis en cache."),
  cacheSaved: w("Saved — applies on the next turn.", "Enregistré — pris en compte au tour suivant."),
  routesTitle: w("Feature routes", "Routes des fonctions"),
  routesLead: w(
    "The models each feature routes to, best first. Read only.",
    "Les modèles vers lesquels chaque fonction est routée, du meilleur au suivant. Lecture seule.",
  ),
  routesEmpty: w(
    "No feature has a route of its own: each follows its category default.",
    "Aucune fonction n'a de route propre : chacune suit le défaut de sa catégorie.",
  ),
  routesError: w("The routes could not be read.", "Les routes n'ont pas pu être lues."),
} as const;

export type FeatureToolsWords = Localised<typeof WORDS>;
export type FeatureToolsLocale = "en" | "fr";
export const featureToolsCopy: Record<FeatureToolsLocale, FeatureToolsWords> = {
  en: localise(WORDS, 0),
  fr: localise(WORDS, 1),
};
