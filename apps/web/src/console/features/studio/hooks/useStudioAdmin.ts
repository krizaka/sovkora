"use client";

import { useCallback, useEffect, useState } from "react";
import { StudioAdminApi } from "@/console/services/studio.admin.api";
import type { BlueprintVersion, StudioSummary } from "@krizaka/orazaka-shared";

export interface StudioAdminState {
  studios: StudioSummary[];
  versions: BlueprintVersion[];
  selected: string | null;
  select: (studioKey: string) => void;
  isLoading: boolean;
  error: string | null;
  reloadVersions: () => void;
}

/**
 * The Builder's catalogue and the selected Studio's version history.
 *
 * Versions reload on demand rather than on a timer: publishing is a deliberate act
 * by the person looking at the screen, so the only moment the list can be stale is
 * one this hook is told about.
 */
export function useStudioAdmin(): StudioAdminState {
  const [studios, setStudios] = useState<StudioSummary[]>([]);
  const [versions, setVersions] = useState<BlueprintVersion[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStudios = useCallback(async () => {
    setIsLoading(true);
    try {
      const catalogue = await StudioAdminApi.listStudios();
      setStudios(catalogue);
      setSelected((current) => current ?? catalogue[0]?.studioKey ?? null);
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Chargement impossible");
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadVersions = useCallback(async () => {
    if (!selected) {
      setVersions([]);
      return;
    }
    try {
      setVersions(await StudioAdminApi.listVersions(selected));
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Chargement impossible");
    }
  }, [selected]);

  useEffect(() => {
    const timer = setTimeout(() => void loadStudios(), 0);
    return () => clearTimeout(timer);
  }, [loadStudios]);

  useEffect(() => {
    const timer = setTimeout(() => void loadVersions(), 0);
    return () => clearTimeout(timer);
  }, [loadVersions]);

  return {
    studios,
    versions,
    selected,
    select: setSelected,
    isLoading,
    error,
    reloadVersions: () => void loadVersions(),
  };
}
