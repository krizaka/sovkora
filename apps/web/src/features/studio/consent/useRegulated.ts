"use client";

import { useQuery } from "@krizaka/query";
import { useTranslation } from "@/core/context/LocaleContext";
import { StudioApi } from "@/services/studio.api";

/**
 * Whether a Studio belongs to a REGULATED pack, so it asks for consent (ADR-055).
 *
 * Read from the pack catalogue, which the server serves: a Studio detail does not carry its pack (`packKey` is null for
 * the Studios a person already holds), the pack lists its Studios. A catalogue that does not answer, or a Studio that is
 * in no pack, is `false`: the note is a courtesy, the 451 on the first run is the guard.
 */
export function useRegulated(studioKey: string): boolean {
  return useRegulatedPack(studioKey) !== null;
}

/** The key of the REGULATED pack a Studio belongs to (what "delete all my data" names), or `null`. */
export function useRegulatedPack(studioKey: string): string | null {
  const { locale } = useTranslation();
  const packs = useQuery({ queryKey: ["studio-packs", locale], queryFn: () => StudioApi.fetchPacks(null, locale) });
  return (
    packs.data?.find((pack) => pack.regulatoryClass === "REGULATED" && pack.studioKeys.includes(studioKey))?.packKey ??
    null
  );
}
