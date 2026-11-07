import type { ActorConsumption } from "@krizaka/orazaka-shared";
import type { ServiceKey } from "@/console/services/organizations.admin.api";

/** One product account of an organization: its keys and what they cost over the period. */
export interface AccountUsage {
  ownerUserId: string;
  account: string;
  keys: ServiceKey[];
  events: number;
  creditsCharged: number;
}

/**
 * Groups the organization's service keys by the account that owns them and reads each account's usage from the billing
 * ledger (its actor is the owner's user id). An account with no usage shows zeros: "idle" and "unknown" are different
 * claims, and the ledger answering with nothing is the first.
 */
export function groupByAccount(keys: ServiceKey[], consumers: ActorConsumption[]): AccountUsage[] {
  const byActor = new Map(consumers.map((row) => [row.actorId, row]));
  const accounts = new Map<string, AccountUsage>();
  for (const key of keys) {
    const existing = accounts.get(key.ownerUserId);
    if (existing) {
      existing.keys.push(key);
      continue;
    }
    const usage = byActor.get(key.ownerUserId);
    accounts.set(key.ownerUserId, {
      ownerUserId: key.ownerUserId,
      account: key.ownerUsername,
      keys: [key],
      events: usage?.events ?? 0,
      creditsCharged: usage?.creditsCharged ?? 0,
    });
  }
  return [...accounts.values()].sort(
    (a, b) => b.creditsCharged - a.creditsCharged || a.account.localeCompare(b.account),
  );
}
