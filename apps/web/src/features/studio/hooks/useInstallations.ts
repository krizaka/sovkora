"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { StudioApi } from "@/services/studio.api";
import type { Installation } from "@krizaka/orazaka-shared";

export interface InstallationsState {
  installations: Installation[];
  isLoading: boolean;
  hasError: boolean;
  reload: () => void;
  findFor: (studioKey: string) => Installation | undefined;
}

/**
 * Everything the signed-in user has installed.
 *
 * `findFor` exists so the detail screen can ask "do I already own this?" against
 * the same list the My Studios tab renders, rather than a second endpoint whose
 * answer could disagree with the tab the user just came from.
 */
export function useInstallations(): InstallationsState {
  const [installations, setInstallations] = useState<Installation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setHasError(false);
    try {
      setInstallations(await StudioApi.fetchInstallations());
    } catch {
      setHasError(true);
      setInstallations([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // Deferred a tick rather than called inline: the load sets state, and doing that
    // synchronously inside an effect is what react-hooks/set-state-in-effect forbids.
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const byStudioKey = useMemo(
    () => new Map(installations.map((installation) => [installation.studioKey, installation])),
    [installations],
  );

  return {
    installations,
    isLoading,
    hasError,
    reload: () => void load(),
    findFor: (studioKey: string) => byStudioKey.get(studioKey),
  };
}
