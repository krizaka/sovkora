/**
 * @file rest-client.ts
 * @description Stateless REST HTTP adapter for the authenticated `/api/v1/*` surface.
 * Every call flows through the catch-all BFF proxy (`app/api/v1/[...path]/route.ts`), which
 * validates the NextAuth session and injects the Bearer token server-side. The browser sends
 * only its session cookie — never a token directly (AGENTS.md §8 — BFF mandatory).
 *
 * Replaces `graphql-client.ts` as part of the REST-only transport consolidation (ADR-028).
 */

import { signOut } from "next-auth/react";

/**
 * An error body: RFC 9457 Problem Details from a service on the Krizaka kit (`detail`, with a
 * stable `code` and the `requestId`), or the legacy `{ error }` of a service not yet migrated.
 */
interface ApiError {
  detail?: string;
  code?: string;
  requestId?: string;
  error?: string;
  /** The Studio service's own error body says it here (`{ status, message }`). */
  message?: string;
}

/**
 * A non-2xx answer, with its status and parsed body, so a caller can act on a refusal that is a question rather
 * than a failure (the 451 of a wellbeing Studio asks for consent). It is still an `Error`: callers that only show
 * `message` are unchanged.
 */
export class RestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly body: unknown,
  ) {
    super(message);
    this.name = "RestError";
  }
}

/** Login route — the sign-out destination, and the one page that must never trigger a sign-out. */
const LOGIN_PATH = "/login";

/**
 * Single-flight latch. `signOut` navigates rather than unmounting the caller, so every in-flight
 * query that 401s would otherwise queue its own sign-out and re-navigate, and each navigation
 * remounts the providers that issued the requests. One teardown per page lifetime is enough; a
 * real sign-out reloads the module and resets this.
 */
let sessionTeardownStarted = false;

/**
 * A NextAuth cookie stays valid (signed, unexpired) even after the session token it carries
 * stops being accepted upstream — e.g. the token expired, was revoked, or its format changed
 * across a backend cutover. Without this, the app renders as signed-in while every call 401s,
 * and only a manual cookie purge recovers it. An upstream 401 is authoritative: drop the local
 * session and send the user back to `/login`.
 *
 * Deliberately inert on `/login`: an anonymous visitor there has no session to tear down, and
 * signing out would bounce them back to the page that just called — a redirect loop.
 */
async function invalidateSession(): Promise<void> {
  if (typeof window === "undefined") return;
  if (sessionTeardownStarted) return;
  if (window.location.pathname === LOGIN_PATH) return;

  sessionTeardownStarted = true;
  await signOut({ callbackUrl: LOGIN_PATH });
}

/**
 * Executes a typed REST request against the BFF `/api/v1/*` proxy.
 *
 * @template T - Expected JSON response shape (parsed; `undefined` for empty bodies).
 * @param path - Absolute app path, e.g. `/api/v1/profile`.
 * @param init - Optional method (default `GET`) and JSON body.
 * @throws {Error} On non-2xx responses, using the server's Problem Details `detail` (or legacy
 *   `error`) when present.
 */
export async function restRequest<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const response = await fetch(path, {
    method: init.method ?? "GET",
    headers: { "Content-Type": "application/json" },
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });

  if (!response.ok) {
    let message = `REST request failed: ${response.status} ${response.statusText}`;
    let body: unknown;
    try {
      const err = (await response.json()) as ApiError;
      body = err;
      const reason = err?.detail ?? err?.error ?? err?.message;
      if (reason) message = reason;
    } catch {
      // Non-JSON error body — keep the status-based message.
    }
    if (response.status === 401) {
      await invalidateSession();
    }
    throw new RestError(message, response.status, body);
  }

  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
