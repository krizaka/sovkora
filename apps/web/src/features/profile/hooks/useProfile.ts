/**
 * @file useProfile.ts
 * @description TanStack Query hook for user profile data.
 * Replaces inline `fetchProfile()` in `ProfileView.tsx`.
 */

import { useQuery } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { ProfileApi } from "@/services/profile.api";
import type { UserProfile } from "@/services/profile.api";

/**
 * Hook providing cached, reactive access to the authenticated user's profile.
 *
 * @returns An object with the profile data, loading state, and error.
 */
export function useProfile() {
  const { status } = useSession();

  const query = useQuery<UserProfile>({
    queryKey: ["profile"],
    queryFn: ProfileApi.fetch,
    enabled: status === "authenticated",
  });

  return {
    profile: query.data ?? null,
    isLoading: query.isLoading,
    error: query.error,
  };
}
