import type { RunArtefact } from "@krizaka/orazaka-shared";

/** An artefact as the Studio service serves it: the label in each language the pack wrote (`labels`), besides the base one. */
type LocalisedArtefact = RunArtefact & { labels?: Record<string, string> };

/** The artefact's label in the page's language: the pack's words for it, else English, else the blueprint's own label. */
export function artefactLabel(artefact: LocalisedArtefact, locale: string): string {
  const labels = artefact.labels ?? {};
  return labels[locale]?.trim() || labels.en?.trim() || artefact.label;
}

/**
 * The shared run schema (zod) drops what it does not know, and the per-language labels are one of those: they are put
 * back from the payload by output key, so the page can show the pack's words for its own language.
 */
export function attachArtefactLabels<T extends { outputs: { key: string }[] }>(run: T, raw: unknown): T {
  const rawOutputs = (raw as { outputs?: unknown } | null)?.outputs;
  if (!Array.isArray(rawOutputs)) {
    return run;
  }
  const labelsByKey = new Map<string, Record<string, string>>();
  for (const output of rawOutputs as { key?: string; labels?: Record<string, string> }[]) {
    if (output?.key && output.labels && typeof output.labels === "object") {
      labelsByKey.set(output.key, output.labels);
    }
  }
  return {
    ...run,
    outputs: run.outputs.map((output) =>
      labelsByKey.has(output.key) ? { ...output, labels: labelsByKey.get(output.key) } : output,
    ),
  };
}
