import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import * as SecureStore from "expo-secure-store";
import { apiRequest } from "../config/api";

/** What the identity service returns from `/api/v1/auth/login` and `/register`. */
export interface AuthSession {
  readonly token: string;
  readonly username: string;
  readonly email: string;
  readonly authorities: readonly string[];
  readonly activeInterceptions: readonly string[];
}

/**
 * The key the JWT is filed under in the device keychain.
 *
 * SecureStore, not AsyncStorage: a bearer token is a credential, and AsyncStorage is plaintext on
 * disk. On a rooted or backed-up device that difference is the whole of the account's security.
 */
const SESSION_KEY = "orazaka.session";

/**
 * What registration produced.
 *
 * Two outcomes because the service has two: with e-mail verification enabled it answers 201 with
 * `{email, requires_verification}` and **no token**, and only otherwise does it issue a session.
 * Collapsing them would store a session whose `token` is undefined and send the actor into the
 * app stack holding nothing — every call then 401s with no explanation.
 */
export type RegistrationOutcome =
  { readonly kind: "signed-in" } | { readonly kind: "verification-required"; readonly email: string };

interface AuthState {
  readonly session: AuthSession | null;
  /** True until the stored session has been read back, so no screen decides during the gap. */
  readonly isRestoring: boolean;
  readonly signIn: (email: string, password: string) => Promise<void>;
  readonly register: (
    username: string,
    email: string,
    password: string,
    language?: string,
  ) => Promise<RegistrationOutcome>;
  readonly signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Holds the signed-in session for the whole app.
 *
 * Before this, `LoginScreen` captured an email and a password into local state and its submit
 * button had no handler at all — there was no session to hold, and every "protected" screen was
 * reachable without one.
 */
export function AuthProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isRestoring, setIsRestoring] = useState(true);

  useEffect(() => {
    void (async () => {
      try {
        const stored = await SecureStore.getItemAsync(SESSION_KEY);
        if (stored) {
          setSession(JSON.parse(stored) as AuthSession);
        }
      } catch {
        // A corrupt or unreadable entry means "not signed in", which is the safe reading.
      } finally {
        setIsRestoring(false);
      }
    })();
  }, []);

  const persist = useCallback(async (next: AuthSession) => {
    setSession(next);
    await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(next));
  }, []);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const next = await apiRequest<AuthSession>("/api/v1/auth/login", {
        method: "POST",
        body: { email, password },
      });
      await persist(next);
    },
    [persist],
  );

  const register = useCallback(
    async (username: string, email: string, password: string, language = "fr"): Promise<RegistrationOutcome> => {
      const body = await apiRequest<Partial<AuthSession> & { requires_verification?: boolean }>(
        "/api/v1/auth/register",
        { method: "POST", body: { username, email, password, language } },
      );

      // The token is the discriminator, not the flag: a body without one cannot be a session
      // whatever else it says, and that is the condition worth guarding.
      if (typeof body.token !== "string") {
        return { kind: "verification-required", email: body.email ?? email };
      }
      await persist(body as AuthSession);
      return { kind: "signed-in" };
    },
    [persist],
  );

  const signOut = useCallback(async () => {
    setSession(null);
    await SecureStore.deleteItemAsync(SESSION_KEY);
  }, []);

  const value = useMemo<AuthState>(
    () => ({ session, isRestoring, signIn, register, signOut }),
    [session, isRestoring, signIn, register, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * The signed-in session and the actions that change it.
 *
 * @returns the auth state
 * @throws Error when called outside {@link AuthProvider}
 */
export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside an AuthProvider");
  }
  return context;
}

/**
 * The bearer token, or null when signed out.
 *
 * A separate hook because most screens want only this — passing the whole session around invites
 * a screen to read `authorities` and make an access decision the backend has already made.
 *
 * @returns the JWT, or null
 */
export function useToken(): string | null {
  return useAuth().session?.token ?? null;
}
