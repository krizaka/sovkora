/**
 * Where an answer stands with the engine's control loop, as the chat stream reports it in the metadata of its
 * chunks (orazaka-ai-engine#125, #157): `control.verdict` is PENDING on every streamed chunk (« en vérification »)
 * until the final event carries the verdict, and `control.badge` names what a degraded verification must show.
 * Nothing here is assumed: no verdict, no claim.
 */
export const CONTROL_VERDICT_KEY = "control.verdict";
export const CONTROL_BADGE_KEY = "control.badge";

export type ControlState = "pending" | "validated" | "degraded" | "not_checked";

export interface ControlStatus {
  readonly state: ControlState;
  /** Set only by a degraded verification (the judge was unavailable): `verification_unavailable`. */
  readonly badge?: string;
}

const STATES: Readonly<Record<string, ControlState>> = {
  PENDING: "pending",
  VALIDATED: "validated",
  DEGRADED: "degraded",
  NOT_CHECKED: "not_checked",
};

/** Reads the verdict of a chunk's metadata; null when the chunk says nothing or something unknown. */
export function parseControl(metadata: unknown): ControlStatus | null {
  if (typeof metadata !== "object" || metadata === null) return null;
  const m = metadata as Record<string, unknown>;
  const raw = m[CONTROL_VERDICT_KEY];
  const state = typeof raw === "string" ? STATES[raw] : undefined;
  if (!state) return null;
  const badge = m[CONTROL_BADGE_KEY];
  return typeof badge === "string" && badge.length > 0 ? { state, badge } : { state };
}

/** What the answer shows: nothing when no control ran, « en vérification » while pending, the badge when degraded. */
export type ControlLabel = "pending" | "validated" | "unavailable" | null;

export function controlLabel(control: ControlStatus | undefined, streaming: boolean): ControlLabel {
  if (!control) return null;
  switch (control.state) {
    case "pending":
      // A pending verdict left over from a stream that ended without a final event is not a live claim.
      return streaming ? "pending" : null;
    case "validated":
      return "validated";
    case "degraded":
      return "unavailable";
    default:
      return null;
  }
}
