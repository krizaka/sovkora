"use client";

import { useCallback, useEffect, useState } from "react";
import { StudioApi } from "@/services/studio.api";
import { useTranslation } from "@/core/context/LocaleContext";
import type { BlueprintVersion, StudioDetail } from "@krizaka/orazaka-shared";

export interface StudioDetailState {
  studio: StudioDetail | null;
  versions: BlueprintVersion[];
  isLoading: boolean;
  notFound: boolean;
  hasError: boolean;
  reload: () => void;
}

/**
 * One Studio's detail plus its version history.
 *
 * Both are fetched together because the detail screen shows both, and a second
 * render pass for the changelog would make the version list appear after the user
 * has already decided.
 *
 * `notFound` is separate from `hasError`: a key that names nothing is a dead link
 * the user should be sent back from, while a failed fetch is worth retrying.
 *
 * @param studioKey - the Studio's stable key
 */
export function useStudioDetail(studioKey: string): StudioDetailState {
  const { locale } = useTranslation();
  const [studio, setStudio] = useState<StudioDetail | null>(null);
  const [versions, setVersions] = useState<BlueprintVersion[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);

  const load = useCallback(async () => {
    if (!studioKey) {
      // Not known yet (a run screen before its run has loaded): nothing to ask for.
      return;
    }
    setIsLoading(true);
    setNotFound(false);
    setHasError(false);
    try {
      const detail = await StudioApi.fetchDetail(studioKey, locale);
      if (!detail) {
        setNotFound(true);
        return;
      }
      setStudio(detail);
      setVersions(await StudioApi.fetchVersions(studioKey));
    } catch {
      setHasError(true);
    } finally {
      setIsLoading(false);
    }
  }, [studioKey, locale]);

  useEffect(() => {
    // Deferred a tick rather than called inline: the load sets state, and doing that
    // synchronously inside an effect is what react-hooks/set-state-in-effect forbids.
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  return { studio, versions, isLoading, notFound, hasError, reload: () => void load() };
}
