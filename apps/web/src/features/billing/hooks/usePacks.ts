"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { PackCategory, PackSubscription, PackSummary } from "@krizaka/orazaka-shared";
import { BillingApi } from "@/services/billing.api";
import { StudioApi } from "@/services/studio.api";

interface PacksState {
  packs: PackSummary[];
  categories: PackCategory[];
  ownedKeys: Set<string>;
  /** Billing did not answer: the shelves are shown, what the user holds is not known. */
  holdingsUnavailable: boolean;
  isLoading: boolean;
  error: string | null;
  pendingKey: string | null;
  reload: () => void;
  /** Settles once the pack is added and the shelves re-read (never rejects: a failure is `error`). */
  subscribe: (packKey: string) => Promise<void>;
  remove: (packKey: string) => Promise<void>;
}

/**
 * The marketplace's data: the shelves, what is on them, and what this actor already holds.
 *
 * Three calls, two services, and the split is the point of ADR-036. The **catalogue** —
 * shelves and cards, already localised and already priced — comes from the Studio context,
 * which owns a pack's identity. **Ownership** comes from billing, which owns the purchase.
 * Their prices travel with the cards: the Studio service asks billing once for the whole
 * page rather than making the browser do it per card.
 *
 * All three are fetched together because a card cannot be rendered from any one alone —
 * a catalogue with no ownership overlay would offer "Add" on a pack the user already
 * bought, which reads as a second charge.
 *
 * `pendingKey` is per-pack rather than a single boolean: a shared flag would disable
 * every card while one is being added, and the user has no way to tell which one they
 * clicked.
 *
 * @param locale - the active locale; the server localises the catalogue, not this hook
 * @param errorMessage - the copy shown when a call fails, in the active locale
 */
export function usePacks(locale: string, errorMessage: string): PacksState {
  const [packs, setPacks] = useState<PackSummary[]>([]);
  const [categories, setCategories] = useState<PackCategory[]>([]);
  const [held, setHeld] = useState<PackSubscription[]>([]);
  const [holdingsUnavailable, setHoldingsUnavailable] = useState(false);
  const [isLoading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingKey, setPendingKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // The catalogue is the Studio service's; what the user holds is billing's, and billing is not deployed
      // everywhere (dev sells nothing). A billing outage must not blank the shelves: the catalogue still tells
      // what each pack is and costs, only the "owned" overlay is unknown.
      const [catalogue, shelves, mine] = await Promise.all([
        StudioApi.fetchPacks(null, locale),
        StudioApi.fetchPackCategories(locale),
        BillingApi.fetchMyPacks().then(
          (held) => ({ held }),
          () => null,
        ),
      ]);
      setPacks(catalogue);
      setCategories(shelves);
      setHeld(mine?.held ?? []);
      setHoldingsUnavailable(mine === null);
    } catch {
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  }, [errorMessage, locale]);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const act = useCallback(
    async (packKey: string, action: () => Promise<unknown>) => {
      setPendingKey(packKey);
      setError(null);
      try {
        await action();
        // Re-read rather than patching local state: the grant of a pack's included
        // credits happens server-side, so the wallet and the ownership list are only
        // consistent with each other after a round trip.
        await load();
      } catch {
        setError(errorMessage);
      } finally {
        setPendingKey(null);
      }
    },
    [errorMessage, load],
  );

  const ownedKeys = useMemo(() => new Set(held.map((subscription) => subscription.packKey)), [held]);

  return {
    packs,
    categories,
    ownedKeys,
    holdingsUnavailable,
    isLoading,
    error,
    pendingKey,
    reload: () => void load(),
    subscribe: (packKey) => act(packKey, () => BillingApi.subscribeToPack(packKey)),
    remove: (packKey) => act(packKey, () => BillingApi.removePack(packKey)),
  };
}
