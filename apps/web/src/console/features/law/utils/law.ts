import type { LawCopy } from "@/console/core/copy/law";
import type { ProposalStatus, Rule, RuleProposal } from "@/console/services/law.schemas";
import { CHECK_TYPES, RULE_METHODS, RULE_SEVERITIES } from "./rule-values.ts";

/**
 * The decisions of Law → Proposals, pure (ADR-089 §8): which action a proposal offers, which ones a kind may promote,
 * how a refusal reads, and the round trip between a rule and the editor's fields. The server is the gate — these only
 * keep the screen from offering what it would refuse.
 */

const APPROVABLE: ReadonlySet<ProposalStatus> = new Set(["PROPOSED"]);
const REJECTABLE: ReadonlySet<ProposalStatus> = new Set(["PROPOSED", "APPROVED"]);
const FROZEN: ReadonlySet<ProposalStatus> = new Set(["PROMOTED", "SUPERSEDED"]);
const PROMOTABLE: ReadonlySet<ProposalStatus> = new Set(["APPROVED"]);

const VET_REFUSED: ReadonlySet<ProposalStatus> = new Set(["REJECTED_AUTO"]);

/** What a decision tells the queue: its words, and how it went. */
export interface Notice {
  tone: "success" | "warning" | "danger";
  text: string;
}

/** Whether a revision passed the vet: its report says so; without one, its status. */
export const passedVet = (p: RuleProposal) => p.vetReport?.passed ?? !VET_REFUSED.has(p.status);

export const canApprove = (p: RuleProposal) => APPROVABLE.has(p.status);
/** A rule already in a packs pull request is decided by that pull request, not here. */
export const canReject = (p: RuleProposal) => REJECTABLE.has(p.status) && !p.packsPrUrl;
/** PROMOTED and SUPERSEDED revisions are history: the newest revision is the one edited. */
export const canEdit = (p: RuleProposal) => !FROZEN.has(p.status);

/** The approved rules of a kind that no packs pull request carries yet. */
export function promotable(proposals: readonly RuleProposal[], kindId: string): RuleProposal[] {
  return proposals.filter((p) => p.kindId === kindId && PROMOTABLE.has(p.status) && !p.packsPrUrl);
}

/** The kinds of the queue, in alphabetical order. */
export function kindsOf(proposals: readonly RuleProposal[]): string[] {
  return [...new Set(proposals.map((p) => p.kindId))].sort((a, b) => a.localeCompare(b));
}

export type RefusalKey = keyof LawCopy["refusals"];

/** The words of a refusal: its Problem `code` when the screen knows it, else what its status says. */
export function refusalKey(code: string | null, status: number, known: LawCopy["refusals"]): RefusalKey {
  if (code && Object.hasOwn(known, code)) return code as RefusalKey;
  if (status === 401 || status === 403) return "forbidden";
  if (status === 400 || status === 409) return "invalid-request";
  return "unavailable";
}

const ISO_DATE = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
/** A calendar date as the API writes it (`YYYY-MM-DD`). */
export const isIsoDate = (text: string) => ISO_DATE.test(text);

/** Text a person typed carries something, spaces aside (the original is what is sent). */
export const hasText = (text: string) => text.trim().length > 0;

export const isHttpsUrl = (text: string) => /^https:\/\/\S+$/.test(text);

/** The editor's fields for a rule: every optional text as `""`, the fixtures one per line. */
/** A value as the screen shows it: its words when it is a known one, else the raw value, else a dash. */
export function wordOf(names: Readonly<Record<string, string>>, value: string | null | undefined): string {
  if (value && Object.hasOwn(names, value)) return names[value];
  return value ?? "—";
}

/** A text as the screen shows it, a dash when there is none. */
export const orDash = (value: string | null | undefined) => (value && hasText(value) ? value : "—");

const knownOr = (values: readonly string[], value: string | null | undefined) =>
  value && values.includes(value) ? value : "";

export const EDITOR_CHECK_TYPES = ["NONE", ...CHECK_TYPES] as const;

/** The editor's fields: a severity, method or check type nobody knows starts empty, to be chosen among the known ones. */
export interface RuleDraft {
  title: string;
  severity: string;
  method: string;
  subject: string;
  checkType: string;
  pattern: string;
  question: string;
  citation: string;
  sourceUrl: string;
  onPass: string;
  onFail: string;
  fixturesPass: string;
  fixturesFail: string;
}

export function draftOf(rule: Rule): RuleDraft {
  return {
    title: rule.title ?? "",
    severity: knownOr(RULE_SEVERITIES, rule.severity),
    method: knownOr(RULE_METHODS, rule.method),
    subject: rule.subject ?? "",
    checkType: rule.check ? knownOr(CHECK_TYPES, rule.check.type) : "NONE",
    pattern: rule.check?.pattern ?? "",
    question: rule.question ?? "",
    citation: rule.citation ?? "",
    sourceUrl: rule.sourceUrl ?? "",
    onPass: rule.onPass ?? "",
    onFail: rule.onFail ?? "",
    fixturesPass: (rule.fixtures?.pass ?? []).join("\n"),
    fixturesFail: (rule.fixtures?.fail ?? []).join("\n"),
  };
}

const lines = (text: string) => text.split("\n").filter(hasText);
const orNull = (text: string) => (hasText(text) ? text : null);

/** The rule the editor's fields describe: the original's id and cited law kept, empty texts sent as null. */
export function ruleOf(original: Rule, draft: RuleDraft): Rule {
  const pass = lines(draft.fixturesPass);
  const fail = lines(draft.fixturesFail);
  return {
    ...original,
    title: draft.title,
    severity: draft.severity,
    method: draft.method,
    subject: orNull(draft.subject),
    check: draft.checkType === "NONE" ? null : { type: draft.checkType, pattern: draft.pattern },
    question: orNull(draft.question),
    citation: draft.citation,
    sourceUrl: draft.sourceUrl,
    onPass: orNull(draft.onPass),
    onFail: orNull(draft.onFail),
    fixtures: pass.length + fail.length > 0 ? { pass, fail } : null,
  };
}
