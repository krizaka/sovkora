import { z } from "zod";
import type { PriceFigures, PriceScheduleResult } from "@krizaka/ui/price-catalogue-editor";
import type { AdminPrice } from "@/console/features/billing/utils/prices";

/**
 * The price catalogue's admin surface (krizaka-billing, `/api/v1/billing/prices/admin`, krizaka/orazaka#417), through
 * the BFF proxy. The amounts live in billing's database and nowhere else (ADR-009): this adapter reads and schedules,
 * it holds no figure. Responses are parsed, not cast — a wrong number here is a wrong price in front of an admin.
 */
const AdminPriceSchema = z.object({
  versionId: z.string(),
  key: z.string(),
  unit: z.string(),
  amountCents: z.number().int().nonnegative().nullable(),
  currency: z.string(),
  taxes: z.string(),
  availability: z.string(),
  limits: z.record(z.string(), z.number().int()),
  effectiveFrom: z.string(),
});

const BASE = "/api/v1/billing/prices/admin";

async function read(path: string): Promise<AdminPrice[]> {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return z.array(AdminPriceSchema).parse(await response.json());
}

export const PricesAdminApi = {
  /** Every entry in force, internal ones included. */
  current: (): Promise<AdminPrice[]> => read(BASE),

  /** Every version of a key, past and scheduled. */
  history: (key: string): Promise<AdminPrice[]> => read(`${BASE}/${encodeURIComponent(key)}/history`),

  /** Schedules the next version of a key from an instant; the service's refusal comes back in words. */
  schedule: async (key: string, change: PriceFigures, effectiveFrom: string): Promise<PriceScheduleResult> => {
    const response = await fetch(`${BASE}/${encodeURIComponent(key)}/schedule`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        unit: change.unit,
        amountCents: change.amountCents,
        availability: change.availability,
        limits: change.limits,
        effectiveFrom,
      }),
    });
    if (response.ok) {
      return { ok: true };
    }
    const message = await response.text().catch(() => "");
    return { ok: false, message: message || `${response.status}` };
  },
};
