import { randomUUID } from "node:crypto";

/**
 * @file session-key.ts
 * @description The key that signs the console's session, `AUTH_CONSOLE_SECRET`: distinct from the public site's, so a
 * session of one host can never be read by the other. In production an unset key fails closed (a throw-away key: no
 * session can be opened), it never falls back to the public site's.
 */
export function consoleAuthKey(env: Record<string, string | undefined>): string | undefined {
  const key = env.AUTH_CONSOLE_SECRET?.trim();
  if (key) return key;
  return env.NODE_ENV === "production" ? randomUUID() : undefined;
}
