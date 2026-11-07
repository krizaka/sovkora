import type { ComposerStudio, Run } from "@krizaka/orazaka-shared";
import { StudioApi } from "@/services/studio.api";

/** User-chosen values for the optional inputs a Studio's schema declares. */
export interface ComposerRunOptions {
  model?: string;
  voice?: string;
  size?: string;
  durationSeconds?: number | string;
}

/**
 * Starts a run from the composer.
 *
 * What replaced `executeFeatureWithPrompt`, and the difference is the whole of M3: that
 * one compiled a `payloadTemplate` the server had handed it and POSTed to a `uriPath` the
 * server had handed it, which meant the browser decided what to call and with what body.
 * This one names a Studio and hands over inputs; the blueprint decides the rest, and the
 * run carries the data class, the retention, the audit trail and the scope guard that a
 * direct capability call never had (ADR-068).
 *
 * The inputs are assembled from what the composer holds and what the Studio declared it
 * wants — never from the Studio's key. The required input takes the attachment or the
 * prose depending on `inputKind`; `promptKey` says where prose goes when the required
 * input is an attachment; the optional values the user picked are sent only when set,
 * because an empty string is not the same as "use your default" to a JSON Schema.
 */
export const startComposerRun = async ({
  studio,
  prompt,
  assetId,
  options,
}: Readonly<{
  studio: ComposerStudio;
  prompt: string;
  assetId?: string;
  options?: ComposerRunOptions;
}>): Promise<Run | null> => {
  const inputs: Record<string, unknown> = {
    [studio.inputKey]: studio.inputKind === "ASSET" ? (assetId ?? "") : prompt,
  };

  if (studio.promptKey && studio.promptKey !== studio.inputKey && prompt.trim()) {
    inputs[studio.promptKey] = prompt;
  }

  for (const [key, value] of Object.entries(options ?? {})) {
    if (value !== undefined && value !== "") {
      inputs[key] = value;
    }
  }

  return StudioApi.startStudioRun(studio.studioKey, inputs);
};
