/** Messages written once for both languages: `w(en, fr)` side by side, `localise(tree, 0 | 1)` reads one language. */

export type Word = readonly [en: string, fr: string];
export const w = (en: string, fr: string): Word => [en, fr];

/** The same tree with every `Word` replaced by its text in one language. */
export type Localised<T> = T extends Word
  ? string
  : T extends readonly (infer U)[]
    ? Localised<U>[]
    : { [K in keyof T]: Localised<T[K]> };

const isWord = (node: unknown): node is Word =>
  Array.isArray(node) && node.length === 2 && typeof node[0] === "string" && typeof node[1] === "string";

export function localise<T>(node: T, index: 0 | 1): Localised<T> {
  if (isWord(node)) return node[index] as Localised<T>;
  if (Array.isArray(node)) return node.map((item) => localise(item, index)) as Localised<T>;
  return Object.fromEntries(
    Object.entries(node as object).map(([key, value]) => [key, localise(value, index)]),
  ) as Localised<T>;
}
