import { RestError } from "@/services/rest-client";

/** What the server asks to be agreed to before a wellbeing Studio starts (451, ADR-055 section 3). */
export interface ConsentRequirement {
  /** The version of the statement in force: the install is refused unless it is sent back unchanged. */
  version: string;
  /** The statement, as the service wrote it. Shown verbatim, never reworded here. */
  statement: string;
  /**
   * The regions the pack installs in, each with its verified crisis line, as the server sends them. Absent when
   * the server sent none (an older service): the fixed list below is then the only one we have.
   */
  regions?: CrisisLine[];
}

/** One region's verified crisis line (official source and the day it was read there). The server's, never ours. */
export interface CrisisLine {
  region: string;
  label: string;
  contact: string;
  availability: string;
  sourceUrl: string;
  verifiedOn: string;
}

/** What an erasure removed: counts only. */
export interface ErasureReceipt {
  packKey: string;
  entries: number;
  installations: number;
  jobs: number;
  /** Conversations whose memory (voice and listening turns) was removed. */
  conversations?: number;
}

function crisisLines(value: unknown): CrisisLine[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const lines = value.filter(
    (line): line is CrisisLine =>
      typeof line === "object" &&
      line !== null &&
      ["region", "label", "contact", "availability", "sourceUrl", "verifiedOn"].every(
        (key) => typeof (line as Record<string, unknown>)[key] === "string",
      ),
  );
  return lines.length > 0 ? lines : undefined;
}

/** The three separate declarations an install of a REGULATED pack carries (`InstallRequest`). */
export interface ConsentAnswer {
  consentVersion: string;
  ageAttested: boolean;
  region: string;
}

/**
 * The regions the wellbeing pack ships a verified crisis resource for; it installs nowhere else (ADR-055 section 5,
 * the pack's `safety.resources`). The service is the judge: a region it refuses comes back as a message we show.
 */
export const CONSENT_REGIONS = ["FR", "BE-FR", "BE-NL", "CA", "CA-QC"] as const;

/** A 451 `consent_required`: not a failure, a question the person has not been asked yet. */
export class ConsentRequiredError extends Error {
  constructor(readonly requirement: ConsentRequirement) {
    super("consent_required");
    this.name = "ConsentRequiredError";
  }
}

/** The consent step a 451 stands for, or the original error when it is anything else. */
export function asConsentStep(error: unknown): unknown {
  if (!(error instanceof RestError) || error.status !== 451) return error;
  const body = error.body as { consentVersion?: unknown; statement?: unknown; regions?: unknown } | undefined;
  if (typeof body?.consentVersion !== "string" || typeof body.statement !== "string") return error;
  return new ConsentRequiredError({
    version: body.consentVersion,
    statement: body.statement,
    regions: crisisLines(body.regions),
  });
}
