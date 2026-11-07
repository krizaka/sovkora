/**
 * @file useVerifyEmail.ts
 * @description TanStack Query mutation hook for email verification.
 * Extracts verification logic from `verify/page.tsx`.
 */

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { AuthApi } from "@/services/auth.api";

/**
 * Hook providing a managed mutation for email token verification.
 *
 * @returns An object with the mutate trigger, status flags, and error message.
 */
export function useVerifyEmail() {
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (token: string) => AuthApi.verifyEmail(token),
    onSuccess: () => {
      setErrorMsg(null);
    },
    onError: (err: Error) => {
      setErrorMsg(err.message || "An error occurred.");
    },
  });

  const resolveStatus = (): "idle" | "loading" | "success" | "error" => {
    if (mutation.isPending) return "loading";
    if (mutation.isSuccess) return "success";
    if (mutation.isError) return "error";
    return "idle";
  };

  return {
    verify: mutation.mutate,
    status: resolveStatus(),
    errorMsg,
  };
}
