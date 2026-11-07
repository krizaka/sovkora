import type { BillableUnit, PricebookRate } from "@krizaka/orazaka-shared";

/** Every unit a rate can be expressed in. */
export const UNITS: BillableUnit[] = [
  "KILOTOKEN",
  "IMAGE_STEP",
  "OUTPUT_SECOND",
  "KILOCHAR",
  "AUDIO_MINUTE",
  "GPU_SECOND",
  "CALL",
];

/** A rate being edited, plus the one flag the form needs and the API must not see. */
export interface ModelRateDraft {
  capability: PricebookRate["capability"];
  modelName: string;
  unit: BillableUnit;
  creditsPerUnit: number;
  minimumCredits: number;
  estimateCredits: number;
  /** Whether this model already had a dedicated rate — governs the publish guard. */
  isReprice: boolean;
}

/** The draft as the API takes it: a rate, not a form. */
export function asRate(draft: ModelRateDraft): Omit<PricebookRate, "version"> {
  return {
    capability: draft.capability,
    modelName: draft.modelName,
    unit: draft.unit,
    creditsPerUnit: draft.creditsPerUnit,
    minimumCredits: draft.minimumCredits,
    estimateCredits: draft.estimateCredits,
  };
}
