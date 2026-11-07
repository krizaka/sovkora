/**
 * @file auth-flags.ts
 * @description Feature flags of the sign-in and sign-up entry points (bounded context AUTH_).
 *
 * `AUTH_LOGIN_ENABLED` and `AUTH_SIGNUP_ENABLED` default to false: the public development site is read-only —
 * no sign-in, no sign-up and no call to the backend (no edge URL is needed to build or run it). They are read
 * on the server (root layout, proxy, API routes) and handed to the client by `AuthFlagsProvider`.
 */

export interface AuthFlags {
  login: boolean;
  signup: boolean;
  /** The GitHub sign-in is offered: its client is configured AND this site accepts new accounts (see {@link providerOffered}). */
  github: boolean;
  /** Same for Google. */
  google: boolean;
}

/** `true`, `1`, `yes` and `on` (any case) switch a flag on; anything else, or nothing, is off. */
export function parseFlag(value: string | undefined): boolean {
  return ["true", "1", "yes", "on"].includes((value ?? "").trim().toLowerCase());
}

/** An OAuth client is configured when both its id and its secret are set. */
export const githubConfigured = (env: Record<string, string | undefined>): boolean =>
  Boolean(env.GITHUB_ID?.trim() && env.GITHUB_SECRET?.trim());
export const googleConfigured = (env: Record<string, string | undefined>): boolean =>
  Boolean(env.GOOGLE_CLIENT_ID?.trim() && env.GOOGLE_CLIENT_SECRET?.trim());

/**
 * A provider button is shown only when its client is configured AND it can sign an existing account in. The gateway's
 * OAuth endpoint provisions an account on first use, so while sign-up is closed it would create one: no button then
 * (and `auth-options` does not register the provider either).
 */
export const providerOffered = (configured: boolean, signup: boolean): boolean => configured && signup;

export function readAuthFlags(env: Record<string, string | undefined> = process.env): AuthFlags {
  const login = parseFlag(env.AUTH_LOGIN_ENABLED);
  const signup = parseFlag(env.AUTH_SIGNUP_ENABLED);
  return {
    login,
    signup,
    github: login && providerOffered(githubConfigured(env), signup),
    google: login && providerOffered(googleConfigured(env), signup),
  };
}

/** Neither way in is open: the site is a showcase and must not reach the backend. */
export function authClosed(flags: AuthFlags): boolean {
  return !flags.login && !flags.signup;
}

const BACKEND_PREFIXES = ["/api/v1", "/api/auth", "/api/chat", "/uploads"];

/** Paths served by the BFF on behalf of a signed-in user — they answer 404 when the site is closed. */
export function isBackendPath(pathname: string): boolean {
  // The sign-up route refuses by itself (403, Dev preview `site.signup-closed`): it is not hidden behind a 404.
  if (pathname === "/api/auth/register") return false;
  return BACKEND_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}
