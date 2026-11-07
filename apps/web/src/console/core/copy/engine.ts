/**
 * @file engine.ts
 * @description The words of the Engine screen, in English and French (one object of the same shape per language).
 */
import { localise, w, type Localised } from "@/core/context/words";

const WORDS = {
  title: w("Engine", "Moteur"),
  lead: w(
    "The engine's runtime settings. A change takes effect on the next read, with no restart.",
    "Les réglages d'exécution du moteur. Un changement prend effet à la lecture suivante, sans redémarrage.",
  ),
  nav: w("Engine", "Moteur"),
  error: w(
    "The settings could not be read: the studio service may be starting.",
    "Les réglages n'ont pas pu être lus : le service des studios démarre peut-être.",
  ),
  retry: w("Retry", "Réessayer"),
  loading: w("Reading the settings", "Lecture des réglages"),
  groups: {
    run: w("Runs, fan-out, cache and events", "Exécutions, répartition, cache et événements"),
    webhook: w("Webhooks", "Webhooks"),
    retention: w("Retention", "Rétention"),
    marketplace: w("Marketplace", "Marketplace"),
    downloads: w("Downloads (public site)", "Téléchargements (site public)"),
    other: w("Other", "Autres"),
  },
  current: w("Current value", "Valeur actuelle"),
  defaultValue: w("Default", "Défaut"),
  bounds: w("Bounds", "Bornes"),
  min: w("min {n}", "min {n}"),
  max: w("max {n}", "max {n}"),
  maxItems: w("up to {n} items", "{n} éléments au plus"),
  listHint: w("One item per line.", "Un élément par ligne."),
  save: w("Save", "Enregistrer"),
  saving: w("Saving", "Enregistrement"),
  saved: w("Saved — applies on the next read.", "Enregistré — pris en compte à la lecture suivante."),
  reset: w("Use the default", "Valeur par défaut"),
  history: w("History", "Historique"),
  hideHistory: w("Hide history", "Masquer l'historique"),
  historyEmpty: w("No change recorded yet.", "Aucun changement enregistré."),
  historyError: w("The history could not be read.", "L'historique n'a pas pu être lu."),
  at: w("When", "Quand"),
  actor: w("Actor", "Auteur"),
  before: w("Before", "Avant"),
  after: w("After", "Après"),
  empty: w("—", "—"),
  on: w("On", "Activé"),
  off: w("Off", "Désactivé"),
  invalid: {
    type: w("Enter a whole number.", "Saisissez un nombre entier."),
    range: w("Must be between {min} and {max}.", "Doit être compris entre {min} et {max}."),
    min: w("Must be at least {n}.", "Doit valoir au moins {n}."),
    max: w("Must be at most {n}.", "Doit valoir au plus {n}."),
    items: w("At most {n} items.", "{n} éléments au plus."),
    empty: w("An item is empty.", "Un élément est vide."),
    duplicate: w("An item appears twice.", "Un élément apparaît deux fois."),
  },
  failed: w("The change was refused.", "Le changement a été refusé."),
} as const;

export type EngineWords = Localised<typeof WORDS>;

export type EngineLocale = "en" | "fr";
export const engineCopy: Record<EngineLocale, EngineWords> = { en: localise(WORDS, 0), fr: localise(WORDS, 1) };

/** The words of a language, with `{n}`-style placeholders filled in. */
export const fill = (text: string, values: Record<string, string | number>) =>
  text.replace(/\{(\w+)\}/g, (_, k: string) => String(values[k] ?? ""));
