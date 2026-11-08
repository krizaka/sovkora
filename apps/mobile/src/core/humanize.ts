/**
 * `CONTENT_MODERATOR` / `not-examined` → `Content moderator` / `Not examined`: the fallback for a code the catalogue has
 * no word for yet — spaced and capitalised, never shown as a code. An empty result returns the input unchanged.
 */
export function humanizeCode(code: string): string {
  const words = code.toLowerCase().replace(/[_-]+/g, " ").trim();
  return words === "" ? code : words.charAt(0).toUpperCase() + words.slice(1);
}
