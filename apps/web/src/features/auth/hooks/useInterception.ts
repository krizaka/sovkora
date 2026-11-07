/**
 * @file useInterception.ts
 * @description TanStack Query hooks for interception schema fetching and resolution.
 * Extracts network logic from `InterceptionForm.tsx`.
 */

import { useQuery, useMutation } from "@tanstack/react-query";
import { InterceptionApi } from "@/services/interception.api";
import type { SchemaDescriptor } from "@/services/interception.api";
import { minutesToMilliseconds } from "date-fns";

/**
 * Hook providing cached access to an interception form schema.
 *
 * @param schemaId - The schema identifier to fetch.
 * @returns An object with the schema data, loading state, and error.
 */
export function useInterceptionSchema(schemaId: string) {
  const query = useQuery<SchemaDescriptor>({
    queryKey: ["interception-schema", schemaId],
    queryFn: () => InterceptionApi.fetchSchema(schemaId),
    staleTime: minutesToMilliseconds(5),
  });

  return {
    schema: query.data ?? null,
    isLoading: query.isLoading,
    error: query.error,
  };
}

interface UseResolveInterceptionOptions {
  interceptionType: string;
  schemaId: string;
  onSuccess: () => void;
  onError: (message: string) => void;
}

/**
 * Hook providing a managed mutation for resolving an interception form.
 *
 * @param options - Configuration including identifiers and callbacks.
 * @returns An object with the mutate trigger and pending state.
 */
export function useResolveInterception({
  interceptionType,
  schemaId,
  onSuccess,
  onError,
}: UseResolveInterceptionOptions) {
  const mutation = useMutation({
    mutationFn: (responses: Record<string, string>) => InterceptionApi.resolve(interceptionType, schemaId, responses),
    onSuccess,
    onError: (err: Error) => {
      onError(err.message || "An error occurred. Please try again.");
    },
  });

  return {
    resolve: mutation.mutate,
    isPending: mutation.isPending,
  };
}
