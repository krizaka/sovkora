import { z } from "zod";
import { AdminRefusal } from "./platform.admin.api";
import {
  ArticleLookupSchema,
  DemandSchema,
  DerivationSchema,
  GoldenSchema,
  ImportResultSchema,
  ImportRowSchema,
  InstrumentSchema,
  PromotionSchema,
  RuleProposalSchema,
  type ArticleLookup,
  type Demand,
  type Derivation,
  type Golden,
  type ImportResult,
  type ImportRow,
  type Instrument,
  type LawLanguage,
  type Promotion,
  type ProposalStatus,
  type Rule,
  type RuleProposal,
} from "./law.schemas";

/**
 * Stateless adapter for the law surface (ADR-089 §8): the review queue, the golden cases and the promotion of a kind,
 * the derivations, the demand and the corpus — through the console's BFF (`/api/v1/**`, AGENTS.md §8), never to a
 * service port. Every answer is parsed; every refusal (RFC 9457 Problem Details) arrives as an `AdminRefusal` carrying
 * its `code`, which the screen words in the operator's language.
 */
const BASE = "/api/v1/admin/law";

async function call(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(`${BASE}${path}`, init);
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as { code?: unknown };
    throw new AdminRefusal(response.status, typeof problem.code === "string" ? problem.code : null);
  }
  return response.json();
}

const json = (method: "POST" | "PUT", body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

const query = (values: Readonly<Record<string, string | undefined>>) => {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) if (value) params.set(key, value);
  const text = params.toString();
  return text ? `?${text}` : "";
};

const id = encodeURIComponent;

export interface PromoteRequest {
  proposalIds: readonly string[];
  effectiveFrom: string;
  revisionNote?: string;
}

export interface DerivationRequest {
  jurisdiction: string;
  kindId: string;
  asOf: string;
  language: LawLanguage;
}

export interface ArticleQuery {
  jurisdiction: string;
  instrument: string;
  ref: string;
  language: LawLanguage;
  asOf: string;
}

export interface ManualImport {
  file: File;
  jurisdiction: string;
  instrument: string;
  language: LawLanguage;
  datasetUrl: string;
  sha256?: string;
  consolidatedTo?: string;
}

export const LawAdminApi = {
  /** The queue, newest first; `status` and `kindId` narrow it (omitted: everything). */
  proposals: async (status?: ProposalStatus, kindId?: string): Promise<RuleProposal[]> =>
    z.array(RuleProposalSchema).parse(await call(`/proposals${query({ status, kindId })}`)),

  /** Approves a PROPOSED rule, with an optional note. */
  approve: async (proposalId: string, note?: string): Promise<RuleProposal> =>
    RuleProposalSchema.parse(await call(`/proposals/${id(proposalId)}/approve`, json("POST", note ? { note } : {}))),

  /** Rejects a rule; the reason is required (the server refuses a blank one as well). */
  reject: async (proposalId: string, reason: string): Promise<RuleProposal> =>
    RuleProposalSchema.parse(await call(`/proposals/${id(proposalId)}/reject`, json("POST", { reason }))),

  /** Saves an edited rule as a new revision, vetted again: PROPOSED when it passes, REJECTED_AUTO with its report otherwise. */
  revise: async (proposalId: string, rule: Rule): Promise<RuleProposal> =>
    RuleProposalSchema.parse(await call(`/proposals/${id(proposalId)}`, json("PUT", rule))),

  /** The golden cases of a kind, read from orazaka-packs `main`. */
  golden: async (kindId: string): Promise<Golden> => GoldenSchema.parse(await call(`/kinds/${id(kindId)}/golden`)),

  /** Opens the orazaka-packs pull request of the chosen approved rules; its merge is the promotion. */
  promote: async (kindId: string, request: PromoteRequest): Promise<Promotion> =>
    PromotionSchema.parse(await call(`/kinds/${id(kindId)}/promote`, json("POST", request))),

  /** Starts a derivation (a Studio run): the engine reads the law of the kind and proposes rules. */
  derive: async (request: DerivationRequest): Promise<Derivation> =>
    DerivationSchema.parse(await call("/derivations", json("POST", request))),

  /** The kinds asked for with no promoted ruleset, highest count first. */
  demand: async (): Promise<Demand[]> => z.array(DemandSchema).parse(await call("/demand")),

  /** The instruments of the corpus with their article and version counts. */
  instruments: async (): Promise<Instrument[]> => z.array(InstrumentSchema).parse(await call("/instruments")),

  /** The imports, newest first. */
  imports: async (limit = 50): Promise<ImportRow[]> =>
    z.array(ImportRowSchema).parse(await call(`/imports?limit=${limit}`)),

  /** Which version of an article was in force on a date — metadata only, never the text. */
  article: async (q: ArticleQuery): Promise<ArticleLookup> =>
    ArticleLookupSchema.parse(await call(`/articles${query({ ...q })}`)),

  /** The manual import of a consolidated XML file (multipart: the browser sets the boundary). */
  importFile: async (upload: ManualImport): Promise<ImportResult> => {
    const form = new FormData();
    form.append("file", upload.file);
    form.append("jurisdiction", upload.jurisdiction);
    form.append("instrument", upload.instrument);
    form.append("language", upload.language);
    form.append("datasetUrl", upload.datasetUrl);
    if (upload.sha256) form.append("sha256", upload.sha256);
    if (upload.consolidatedTo) form.append("consolidatedTo", upload.consolidatedTo);
    return ImportResultSchema.parse(await call("/imports", { method: "POST", body: form }));
  },
} as const;
