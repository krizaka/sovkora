/**
 * Where the last answer was computed, as the engine reports it in the chat stream's `routing` event.
 * Nothing here is assumed: no event, no claim.
 */
export interface ChatRouting {
  /** The provider that answered (e.g. "ollama", "openai"). */
  readonly provider: string;
  /** True only when nothing left the operator's network. */
  readonly local: boolean;
  /** Estimated cost of the answer, in credits (omitted when unknown). */
  readonly estimatedCredits?: number;
}

/** Validates an SSE-parsed `routing` payload; null when it does not say enough to be trusted. */
export function parseRouting(value: unknown): ChatRouting | null {
  if (typeof value !== "object" || value === null) return null;
  const v = value as { provider?: unknown; local?: unknown; estimatedCredits?: unknown };
  if (typeof v.provider !== "string" || v.provider.length === 0 || typeof v.local !== "boolean") return null;
  const credits =
    typeof v.estimatedCredits === "number" && Number.isFinite(v.estimatedCredits) && v.estimatedCredits >= 0
      ? Math.round(v.estimatedCredits)
      : undefined;
  return { provider: v.provider, local: v.local, ...(credits === undefined ? {} : { estimatedCredits: credits }) };
}

/** What the status line under the composer says; every part is optional and only present when true. */
export interface RoutingStatus {
  /** "Runs locally · nothing leaves your network" — only when the last routing was local. */
  readonly localOnly: boolean;
  /** "Routed to <provider>" — when the last answer left the network. */
  readonly routedTo: string | null;
  readonly credits: number | null;
}

export function routingStatus(routing: ChatRouting | null): RoutingStatus {
  if (!routing) return { localOnly: false, routedTo: null, credits: null };
  return {
    localOnly: routing.local,
    routedTo: routing.local ? null : routing.provider,
    credits: routing.estimatedCredits ?? null,
  };
}
