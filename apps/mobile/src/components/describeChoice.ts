import type { Choice } from "../core/inputSchema";

/** The words a picker needs, already translated (`studios.form.*`). */
export interface ChoiceWords {
  readonly local: string;
  readonly cloud: string;
  readonly fast: string;
  readonly balanced: string;
  readonly slow: string;
  readonly about: (seconds: number) => string;
  readonly perRun: (price: string) => string;
  readonly free: string;
}

/** US cents as a price a person reads: 8 → "$0.08". */
export const dollars = (cents: number): string => `$${(cents / 100).toFixed(2)}`;

/**
 * The line under a choice: where it runs, how fast, about how long, what it costs — "Cloud · Fast · about 8 s · $0.08
 * per run". A model that is off says why instead. Facts the catalogue does not have are left out, never guessed.
 */
export function describeChoice(choice: Choice, words: ChoiceWords): string {
  if (!choice.available && choice.unavailableReason !== null) return choice.unavailableReason;
  const parts: string[] = [];
  if (choice.runsOn !== null) parts.push(words[choice.runsOn]);
  if (choice.speed !== null) parts.push(words[choice.speed]);
  if (choice.typicalSeconds !== null) parts.push(words.about(choice.typicalSeconds));
  if (choice.priceCents !== null) parts.push(words.perRun(dollars(choice.priceCents)));
  else if (choice.runsOn === "local") parts.push(words.free);
  return parts.join(" · ");
}
