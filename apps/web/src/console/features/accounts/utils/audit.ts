import type { AdminAuditEntry } from "@/console/services/platform.schemas";

/** The services whose operator audit the console reads. */
export type AuditSource = "identity" | "billing" | "studio" | "engine";

/** One line of the console's Audit log, whatever service wrote it. */
export interface AuditRow {
  key: string;
  source: AuditSource;
  at: string;
  actor: string;
  action: string;
  targetId: string;
  detail: string;
  requestId: string;
  outcome: "ok" | "refused" | "failed";
}

/** A line of billing's operator audit (`GET /api/v1/billing/admin/audit`). */
export interface BillingAuditLine {
  id: number;
  at: string;
  actor: string;
  action: string;
  targetId: string | null;
  requestId: string | null;
  outcome: "ok" | "refused" | "failed";
}

export const identityLine = (e: AdminAuditEntry): AuditRow => ({
  key: `identity:${e.id}`,
  source: "identity",
  at: e.at ?? "",
  actor: e.actor ?? "",
  action: e.action,
  targetId: e.targetId ?? "",
  detail: e.detail ?? "",
  requestId: e.requestId ?? "",
  outcome: e.outcome,
});

export const serviceLine = (source: Exclude<AuditSource, "identity">, l: BillingAuditLine): AuditRow => ({
  key: `${source}:${l.id}`,
  source,
  at: l.at,
  actor: l.actor,
  action: l.action,
  targetId: l.targetId ?? "",
  detail: "",
  requestId: l.requestId ?? "",
  outcome: l.outcome,
});

/** Every source in one list, newest first; a source that did not answer (`null`, or absent) leaves the others. */
export function mergeAudit(
  identity: readonly AdminAuditEntry[] | null,
  services: Partial<Record<Exclude<AuditSource, "identity">, readonly BillingAuditLine[] | null>>,
): AuditRow[] {
  const rows = [...(identity ?? []).map(identityLine)];
  for (const source of ["billing", "studio", "engine"] as const)
    rows.push(...(services[source] ?? []).map((l) => serviceLine(source, l)));
  return rows.sort((a, b) => (a.at < b.at ? 1 : a.at > b.at ? -1 : 0));
}
