import { MODELS } from "@/features/landing/utils/models";
import { normaliseKey } from "@/features/jobs/utils/jobFacts";

/** The registry's display names, by normalised id and name: "wan2.2-ti2v-5b" → "Wan 2.2 TI2V 5B". */
export const MODEL_NAMES: ReadonlyMap<string, string> = new Map(
  MODELS.flatMap((e) => [[normaliseKey(e.id), e.name] as const, [normaliseKey(e.name), e.name] as const]),
);
