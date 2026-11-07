/**
 * @file rule-values.ts
 * @description The values a ruleset knows (orazaka-packs' ruleset schema): what the editor offers. A stored proposal may
 * hold others — the model's own words, refused by the vet — and the screen shows those raw.
 */
export const RULE_SEVERITIES = ["BLOCKING", "MAJOR", "ADVISORY"] as const;
export const RULE_METHODS = ["DETERMINISTIC", "JUDGMENT"] as const;
export const CHECK_TYPES = ["REQUIRED_PATTERN", "FORBIDDEN_PATTERN"] as const;
