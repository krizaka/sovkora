/**
 * @file useAuth.ts
 * @description Hook managing user authentication session integration.
 * Wraps NextAuth React session states to expose unified properties for views.
 *
 * State management: Exposes user entity details, loading states, and redirect options.
 */

import { useSession, signIn, signOut } from "next-auth/react";

/**
 * Custom React Hook providing authentication helpers and current session states.
 * Delegates actual credentials and OAuth flow processing down to next-auth adapters.
 *
 * @returns An object containing session details, loading status flags, and login/logout trigger closures.
 */
export function useAuth() {
  const { data: session, status } = useSession();

  const isAuthenticated = status === "authenticated";
  const isLoading = status === "loading";
  const user = session?.user;

  /**
   * Initiates authentication using GitHub OAuth providers.
   */
  const loginWithGithub = () => signIn("github", { callbackUrl: "/" });

  /**
   * Initiates authentication using Google OAuth providers.
   */
  const loginWithGoogle = () => signIn("google", { callbackUrl: "/" });

  // Example for credentials/orazaka-gateway via TanStack query logic vs next-auth
  // For credentials, you'd typically call signIn("credentials", { ... })

  /**
   * Clears the session and redirects the browser back to the login gateway.
   */
  const logout = () => signOut({ callbackUrl: "/login" });

  return {
    session,
    user,
    isAuthenticated,
    isLoading,
    loginWithGithub,
    loginWithGoogle,
    logout,
  };
}
