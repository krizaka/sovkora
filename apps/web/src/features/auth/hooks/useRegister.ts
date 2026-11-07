/**
 * @file useRegister.ts
 * @description TanStack Query mutation hook for user registration.
 * Extracts registration logic from `RegisterForm.tsx`.
 */

import { useMutation } from "@tanstack/react-query";
import { signIn } from "next-auth/react";
import { useRouter } from "next/navigation";
import { AuthApi } from "@/services/auth.api";
import type { RegisterPayload } from "@/services/auth.api";

interface UseRegisterOptions {
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}

/**
 * Hook providing a managed mutation for user registration with auto-login.
 *
 * @param options - Callbacks for success and error notifications.
 * @returns An object with the mutate trigger and pending state.
 */
export function useRegister({ onSuccess, onError }: UseRegisterOptions) {
  const router = useRouter();

  const mutation = useMutation<unknown, { status: number; error?: string }, RegisterPayload>({
    mutationFn: AuthApi.register,
    onSuccess: async (_, variables) => {
      onSuccess("Account created! Signing you in…");

      const signInRes = await signIn("credentials", {
        email: variables.email,
        password: variables.password,
        redirect: false,
      });

      if (signInRes?.ok) {
        router.push("/");
      } else {
        onSuccess("Account created! Redirecting to login…");
        setTimeout(() => router.push("/login"), 1500);
      }
    },
    onError: (err) => {
      if (err.status === 409) {
        onError(err.error || "An account with this email already exists.");
      } else if (err.error) {
        onError(err.error);
      } else {
        onError("Registration failed. Please try again.");
      }
    },
  });

  return {
    register: mutation.mutate,
    isPending: mutation.isPending,
  };
}
