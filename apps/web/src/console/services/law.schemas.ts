import { z } from "zod";

/**
 * What the law surface of the conversation service returns (ADR-089 §8, `/api/v1/admin/law/**`), parsed on arrival:
 * the review queue is where a person decides whether a rule reaches every tenant's documents, so a misread shape is a
 * wrong decision, not a wrong pixel. Dates are ISO `YYYY-MM-DD`, instants ISO-8601 UTC strings.
 */

export const PROPOSAL_STATUSES = [
  "PROPOSED",
  "REJECTED_AUTO",
  "APPROVED",
  "REJECTED",
  "PROMOTED",
  "SUPERSEDED",
] as const;
export type ProposalStatus = (typeof PROPOSAL_STATUSES)[number];

export const LAW_LANGUAGES = ["fr", "en"] as const;
export type LawLanguage = (typeof LAW_LANGUAGES)[number];

/**
 * A rule and its law blocks are read leniently: a proposal the vet refused (`SCHEMA_INVALID`) is stored exactly as the
 * model wrote it — fields missing or null, a severity or check type nobody knows. One such row must never hide the
 * queue; the screen shows the raw value (or a dash) and its editor offers only the known values.
 */
export const LawBlockSchema = z.object({
  instrument: z.string().nullish(),
  ref: z.string().nullish(),
  language: z.string().nullish(),
  contentHash: z.string().nullish(),
  quote: z.string().nullish(),
});
export type LawBlock = z.infer<typeof LawBlockSchema>;

export const RuleSchema = z.object({
  ruleId: z.string().nullish(),
  title: z.string().nullish(),
  severity: z.string().nullish(),
  method: z.string().nullish(),
  subject: z.string().nullish(),
  check: z.object({ type: z.string().nullish(), pattern: z.string().nullish() }).nullish(),
  question: z.string().nullish(),
  citation: z.string().nullish(),
  sourceUrl: z.string().nullish(),
  onPass: z.string().nullish(),
  onFail: z.string().nullish(),
  fixtures: z.object({ pass: z.array(z.string()).default([]), fail: z.array(z.string()).default([]) }).nullish(),
  law: z.array(LawBlockSchema).nullish(),
});
export type Rule = z.infer<typeof RuleSchema>;

export const VetReportSchema = z.object({
  vetVersion: z.string(),
  asOf: z.string(),
  passed: z.boolean(),
  findings: z.array(z.object({ reason: z.string(), detail: z.string().nullish() })).default([]),
});
export type VetReport = z.infer<typeof VetReportSchema>;

export const RuleProposalSchema = z.object({
  id: z.string(),
  jurisdiction: z.string(),
  kindId: z.string(),
  ruleId: z.string(),
  proposalRevision: z.number(),
  status: z.enum(PROPOSAL_STATUSES),
  rule: RuleSchema,
  vetReport: VetReportSchema.nullish(),
  derivationRunId: z.string().nullish(),
  reviewedBy: z.string().nullish(),
  reviewedAt: z.string().nullish(),
  decisionNote: z.string().nullish(),
  packsPrUrl: z.string().nullish(),
  createdAt: z.string(),
});
export type RuleProposal = z.infer<typeof RuleProposalSchema>;

export const GoldenSchema = z.object({
  kindId: z.string(),
  required: z.number(),
  documents: z.number(),
  compliant: z.number(),
  defective: z.number(),
  blank: z.number(),
  met: z.boolean(),
  cases: z.array(z.object({ name: z.string(), form: z.string() })).default([]),
});
export type Golden = z.infer<typeof GoldenSchema>;

export const PromotionSchema = z.object({
  prUrl: z.string(),
  branch: z.string(),
  rulesetPath: z.string(),
  revision: z.number(),
  proposalIds: z.array(z.string()),
});
export type Promotion = z.infer<typeof PromotionSchema>;

export const DerivationSchema = z.object({
  runId: z.string(),
  outcome: z.string(),
  proposals: z.number(),
  rejected: z.number(),
  creditsSpent: z.number(),
});
export type Derivation = z.infer<typeof DerivationSchema>;

export const DemandSchema = z.object({
  jurisdiction: z.string(),
  kindId: z.string(),
  count: z.number(),
  firstSeen: z.string(),
  lastSeen: z.string(),
});
export type Demand = z.infer<typeof DemandSchema>;

export const InstrumentSchema = z.object({
  jurisdiction: z.string(),
  code: z.string(),
  titleFr: z.string().nullish(),
  titleEn: z.string().nullish(),
  sourceKey: z.string(),
  articles: z.number(),
  versions: z.number(),
});
export type Instrument = z.infer<typeof InstrumentSchema>;

export const ArticleVersionSchema = z.object({
  inForceFrom: z.string(),
  inForceTo: z.string().nullish(),
  contentHash: z.string(),
  officialUrl: z.string(),
  importId: z.string(),
  length: z.number(),
});
export type ArticleVersion = z.infer<typeof ArticleVersionSchema>;

export const ArticleLookupSchema = z.object({
  jurisdiction: z.string(),
  instrument: z.string(),
  ref: z.string(),
  language: z.string(),
  asOf: z.string(),
  inForce: ArticleVersionSchema.nullish(),
  history: z.array(ArticleVersionSchema).default([]),
});
export type ArticleLookup = z.infer<typeof ArticleLookupSchema>;

export const ImportRowSchema = z.object({
  id: z.string(),
  sourceKey: z.string(),
  mode: z.string(),
  datasetUrl: z.string().nullish(),
  datasetSha256: z.string().nullish(),
  consolidatedTo: z.string().nullish(),
  importedBy: z.string().nullish(),
  startedAt: z.string(),
  finishedAt: z.string().nullish(),
  articlesNew: z.number(),
  articlesChanged: z.number(),
  articlesUnchanged: z.number(),
  status: z.string(),
});
export type ImportRow = z.infer<typeof ImportRowSchema>;

export const ImportResultSchema = z.object({
  unchanged: z.number(),
  stored: z.number(),
  requests: z.array(z.string()).default([]),
  failures: z.array(z.string()).default([]),
  imports: z.array(z.string()).default([]),
});
export type ImportResult = z.infer<typeof ImportResultSchema>;
