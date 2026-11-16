/**
 * @file development-status.ts
 * @description The one line every Sovkora surface says about the product's state (krizaka/orazaka#516,
 * CLI parity orazaka-cli#109): in development, still being tested. No « ready » wording. EN/FR, the
 * language read from ORAZAKA_LANG, then LC_ALL / LC_MESSAGES / LANG (English by default).
 */

export type CliLocale = "en" | "fr";

export const DEVELOPMENT_STATUS: Readonly<Record<CliLocale, string>> = {
  en: "In development: Sovkora is still being tested. Expect changes.",
  fr: "En développement : Sovkora est encore en cours de test. Attendez-vous à des changements.",
};

export function resolveLocale(vars: NodeJS.ProcessEnv = process.env): CliLocale {
  const raw = vars.ORAZAKA_LANG ?? vars.LC_ALL ?? vars.LC_MESSAGES ?? vars.LANG ?? "";
  return raw.toLowerCase().startsWith("fr") ? "fr" : "en";
}

export function developmentStatus(vars: NodeJS.ProcessEnv = process.env): string {
  return DEVELOPMENT_STATUS[resolveLocale(vars)];
}
