"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { AuthFlags } from "@/core/config/auth-flags";

const CLOSED: AuthFlags = { login: false, signup: false, github: false, google: false };
const AuthFlagsContext = createContext<AuthFlags>(CLOSED);

/** Hands the server-read flags (AUTH_LOGIN_ENABLED, AUTH_SIGNUP_ENABLED) to the client tree. */
export function AuthFlagsProvider({ flags, children }: Readonly<{ flags: AuthFlags; children: ReactNode }>) {
  return <AuthFlagsContext.Provider value={flags}>{children}</AuthFlagsContext.Provider>;
}

export function useAuthFlags(): AuthFlags {
  return useContext(AuthFlagsContext);
}
