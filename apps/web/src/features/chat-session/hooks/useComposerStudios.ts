"use client";

import { useQuery } from "@krizaka/query";
import type { ComposerStudio } from "@krizaka/orazaka-shared";
import { StudioApi } from "@/services/studio.api";

/** How often the row is re-read so a Studio greys out or comes back without a reload. */
const REFRESH_MS = 10000;

/**
 * The chat composer's button row.
 *
 * Replaces `useBootstrapFeatures`, which polled `/api/v1/features` — the capability
 * rows that carried a URI and a payload template the client then POSTed to. The row is
 * served from Studios now (ADR-068 §3), so a click starts a run and the blueprint says
 * what happens next: same buttons, one door.
 *
 * Kept on the same cadence for the same reason: `available` moves when an inference
 * engine goes offline or an entitlement changes, and the row must follow without a
 * page reload.
 *
 * @param locale - the caller's locale; label and icon come from the pack's own i18n
 */
export function useComposerStudios(locale: string): ComposerStudio[] {
  const studios = useQuery({
    queryKey: ["composer-studios", locale],
    queryFn: () => StudioApi.fetchComposerStudios(locale),
    refetchInterval: REFRESH_MS,
  });
  return studios.data ?? [];
}
