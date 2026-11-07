/**
 * Fixtures of Law → Proposals (ADR-089 §8): the wire shapes of `/api/v1/admin/law/**`, with the public text of the
 * Civil Code of Québec (art. 1931) and fictional people (the persona Eric). Shared by the console's tests.
 */
import type { RuleProposal } from "@/console/services/law.schemas";

export const QUOTE =
  "de donner au locataire un préavis de vingt-quatre heures de son intention de vérifier l’état du bien";
export const OFFICIAL_URL = "https://www.legisquebec.gouv.qc.ca/fr/document/lc/CCQ-1991#se:1931";
export const HASH = "a".repeat(64);

export function proposal(overrides: Partial<RuleProposal> = {}): RuleProposal {
  return {
    id: "11111111-1111-4111-8111-111111111111",
    jurisdiction: "QC",
    kindId: "qc-visit-notice",
    ruleId: "QC-VISIT-001",
    proposalRevision: 1,
    status: "PROPOSED",
    rule: {
      ruleId: "QC-VISIT-001",
      title: "The notice gives the tenant 24 hours",
      severity: "BLOCKING",
      method: "DETERMINISTIC",
      subject: null,
      check: { type: "REQUIRED_PATTERN", pattern: "24\\s*heures" },
      question: null,
      citation: "C.c.Q., art. 1931",
      sourceUrl: OFFICIAL_URL,
      onPass: "The notice period is stated.",
      onFail: "The notice must give at least 24 hours.",
      fixtures: { pass: ["Visite prévue avec un préavis de 24 heures."], fail: ["Visite demain matin."] },
      law: [{ instrument: "CCQ-1991", ref: "1931", language: "fr", contentHash: HASH, quote: QUOTE }],
    },
    vetReport: { vetVersion: "1", asOf: "2026-10-20", passed: true, findings: [] },
    derivationRunId: "22222222-2222-4222-8222-222222222222",
    reviewedBy: null,
    reviewedAt: null,
    decisionNote: null,
    packsPrUrl: null,
    createdAt: "2026-10-21T14:00:00Z",
    ...overrides,
  };
}
