/**
 * @file auth-flags.ts
 * @description Feature flag of the operator sign-in (bounded context AUTH_).
 *
 * `AUTH_CONSOLE_LOGIN_ENABLED` defaults to false and is the console's own: opening sign-in on the public site
 * (`AUTH_LOGIN_ENABLED`) never opens the console. Closed, the console shows a notice — no sign-in, no session and no
 * call to the backend. Read on the server only. `AUTH_SIGNUP_ENABLED` is kept for parity (a console has no sign-up).
 */

export interface AuthFlags {
  login: boolean;
  signup: boolean;
}

/** `true`, `1`, `yes` and `on` (any case) switch a flag on; anything else, or nothing, is off. */
export function parseFlag(value: string | undefined): boolean {
  return ["true", "1", "yes", "on"].includes((value ?? "").trim().toLowerCase());
}

export function readAuthFlags(env: Record<string, string | undefined> = process.env): AuthFlags {
  return { login: parseFlag(env.AUTH_CONSOLE_LOGIN_ENABLED), signup: parseFlag(env.AUTH_SIGNUP_ENABLED) };
}

/** Neither way in is open: the site must not reach the backend. */
export function authClosed(flags: AuthFlags): boolean {
  return !flags.login && !flags.signup;
}

const BACKEND_PREFIXES = ["/api/v1", "/api/auth"];

/** Paths served by the BFF on behalf of an operator — they answer 404 when the site is closed. */
export function isBackendPath(pathname: string): boolean {
  return BACKEND_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}
