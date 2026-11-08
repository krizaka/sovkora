"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { StudioApi } from "@/services/studio.api";
import { useTranslation } from "@/core/context/LocaleContext";
import type { StudioSummary } from "@krizaka/orazaka-shared";

export interface StudiosState {
  studios: StudioSummary[];
  professions: string[];
  isLoading: boolean;
  hasError: boolean;
  reload: () => void;
}

/** The distinct professions of the catalogue, alphabetical (the filter tabs' order). */
export function professionsOf(studios: readonly Pick<StudioSummary, "profession">[]): string[] {
  return Array.from(new Set(studios.map((studio) => studio.profession))).sort((a, b) => a.localeCompare(b));
}

/**
 * The Studio catalogue for the signed-in user.
 *
 * The profession filter is applied client-side over one fetch rather than a
 * request per tab: the catalogue is small and bounded by the number of trades the
 * product supports, so refetching on every filter click would spend a round trip
 * to remove rows already in memory.
 *
 * @param profession - the trade to narrow to, or null for everything
 */
export function useStudios(profession: string | null): StudiosState {
  const { locale } = useTranslation();
  const [studios, setStudios] = useState<StudioSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setHasError(false);
    try {
      setStudios(await StudioApi.fetchCatalogue(null, locale));
    } catch {
      // An empty grid and a failed grid are different screens: the second offers
      // a retry, the first offers the other tab.
      setHasError(true);
      setStudios([]);
    } finally {
      setIsLoading(false);
    }
  }, [locale]);

  useEffect(() => {
    // Deferred a tick rather than called inline: the load sets state, and doing that
    // synchronously inside an effect is what react-hooks/set-state-in-effect forbids.
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const professions = useMemo(() => professionsOf(studios), [studios]);

  const visible = useMemo(
    () => (profession ? studios.filter((studio) => studio.profession === profession) : studios),
    [studios, profession],
  );

  return { studios: visible, professions, isLoading, hasError, reload: () => void load() };
}
