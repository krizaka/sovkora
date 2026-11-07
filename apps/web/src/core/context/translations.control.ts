/**
 * @file translations.control.ts
 * @description The words of the control badge on an answer (orazaka-ai-engine#125, #157): « en vérification » while the
 * engine checks it, the verdict once final, and what a degraded verification says (en, fr).
 */
import { localise, w } from "./words";

const WORDS = {
  pending: w("Verifying…", "En vérification…"),
  validated: w("Verified", "Vérifiée"),
  unavailable: w("Verification unavailable", "Vérification indisponible"),
  unavailableHint: w(
    "The deterministic checks passed; the judge was unavailable, so the answer was not reviewed in depth.",
    "Les contrôles déterministes sont passés ; le juge était indisponible, la réponse n'a pas été relue en profondeur.",
  ),
};

export const control = {
  en: localise(WORDS, 0),
  fr: localise(WORDS, 1),
};

export type ControlDictionary = typeof control.en;
