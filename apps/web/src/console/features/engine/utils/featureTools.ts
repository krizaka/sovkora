import { fill } from "@/console/core/copy/engine";
import type { FeatureToolsWords } from "@/console/core/copy/featureTools";

export type ParsedRounds = { ok: true; value: number } | { ok: false; error: string };

/** What was typed as a number of rounds: a whole number inside the server's range, or the words of what is wrong. */
export function parseRounds(
  draft: string,
  range: { min: number; max: number },
  words: FeatureToolsWords,
): ParsedRounds {
  const text = draft.trim();
  const n = Number(text);
  if (!/^\d+$/.test(text) || n < range.min || n > range.max) {
    return { ok: false, error: fill(words.invalidRange, range) };
  }
  return { ok: true, value: n };
}
