/**
 * @file brand.ts
 * @description The name the product shows. It is the administrator's `brand.displayName` setting, else the commercial
 * name of the seed (`@krizaka/org`); the messages carry a `{brand}` placeholder, never the typed name.
 */

import { displayName } from "@krizaka/org";

/** The commercial name, shown until the setting says otherwise. A code name is refused (see {@link brandName}). */
export const DEFAULT_BRAND = displayName("orazaka");

/** The internal name: the repository, the packages, the addresses. Shown only as the dev-deployment line under the commercial name. */
export const CODE_NAME = "orazaka";

const CODE_NAME_PATTERN = /orazaka/i;

/** The name to show for a setting value: the trimmed value, else the default; a value naming the code name is refused. */
export function brandName(value: string | null | undefined): string {
  const name = displayName("orazaka", value);
  return CODE_NAME_PATTERN.test(name) ? DEFAULT_BRAND : name;
}

/** Fills `{brand}` in every string of a dictionary, leaving the other placeholders (`{count}`) for `fill`. */
export function withBrand<T>(messages: T, brand: string): T {
  if (typeof messages === "string") {
    return messages.replaceAll("{brand}", brand) as T;
  }
  if (Array.isArray(messages)) {
    return messages.map((item: unknown) => withBrand(item, brand)) as T;
  }
  if (messages && typeof messages === "object") {
    return Object.fromEntries(Object.entries(messages).map(([key, value]) => [key, withBrand(value, brand)])) as T;
  }
  return messages;
}
