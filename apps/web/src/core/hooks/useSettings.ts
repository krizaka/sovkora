/**
 * @file useSettings.ts
 * @description Custom hook for fetching and updating user configurations via TanStack Query.
 * Delegates all network I/O to the stateless {@link SettingsApi} service adapter.
 *
 * State management: Employs TanStack Query to manage query states, loading states, and mutations.
 * Optimization: Performs optimistic query cache updates on successful preference mutations.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { SettingsApi } from "@/services/settings.api";
import type { Settings } from "@/core/types/settings.types";

/**
 * Custom React Hook to consume global application visual styles, layouts, and languages.
 * Provides caching and loading flags using TanStack Query underneath.
 *
 * @returns An object containing the query data, query loading state, mutator action, and mutator progress.
 */
export function useSettings() {
  const queryClient = useQueryClient();
  const { status } = useSession();

  // `LocaleProvider`/`TenantProvider` are mounted globally, so this hook also runs on the
  // anonymous pages (`/login`, `/register`). Settings live behind `/api/v1/profile` — firing
  // the query without a session guarantees a 401, which the REST client reads as an expired
  // session and turns into a sign-out. Only ask once there is an identity to ask about.
  const query = useQuery({
    queryKey: ["settings"],
    queryFn: SettingsApi.fetch,
    enabled: status === "authenticated",
  });

  const mutation = useMutation({
    mutationFn: SettingsApi.update,
    onSuccess: (updatedPrefs) => {
      // Optimistically update the settings query cache
      queryClient.setQueryData(["settings"], (old: Settings | undefined) => {
        if (!old) return old;
        return {
          ...old,
          language: updatedPrefs.language || old.language,
          autoSave: updatedPrefs.autoSave ?? old.autoSave,
          aiPersona: updatedPrefs.aiPersona || old.aiPersona,
          themeName: updatedPrefs.themeName || old.themeName,
          themeTagline: updatedPrefs.themeTagline || old.themeTagline,
          themeAccent: updatedPrefs.themeAccent || old.themeAccent,
          themeLayout: updatedPrefs.themeLayout || old.themeLayout,
          theme: updatedPrefs.theme || old.theme,
          tenantId: updatedPrefs.tenantId || old.tenantId,
        };
      });
    },
  });

  return {
    settings: query.data,
    isLoading: query.isLoading,
    updateSettings: mutation.mutate,
    isUpdating: mutation.isPending,
  };
}
