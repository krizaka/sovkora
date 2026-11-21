import "server-only";
import type { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";

/**
 * @file backend-token.ts
 * @description The identity service's session JWT, read by the BFF from NextAuth's encrypted, httpOnly session cookie.
 * The session object (`/api/auth/session`, `useSession`) is the page's and never carries it (krizaka-restricted#347).
 */

/** Where the session lives when it is not NextAuth's default cookie and key (the console's own). */
export interface SessionCookie {
  cookieName?: string;
  secret?: string;
}

/** The Bearer token the BFF replays to the backend, or null without a valid session. */
export async function backendToken(req: NextRequest, cookie: SessionCookie = {}): Promise<string | null> {
  const jwt = await getToken({ req, ...cookie });
  return jwt?.backendToken ?? null;
}
