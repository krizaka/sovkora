/** @file security-headers.ts @description Response headers of every page: no framing, no sniffing, HTTPS only, scripts by nonce. */

/** The policy of what no page may do: be framed, move its base URL, embed a plugin. */
const BASE_POLICY = "frame-ancestors 'none'; base-uri 'self'; object-src 'none'";

export const SECURITY_HEADERS: { key: string; value: string }[] = [
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  // The floor of what the proxy does not answer (static files); every page gets `contentSecurityPolicy` from the proxy.
  { key: "Content-Security-Policy", value: BASE_POLICY },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(self), geolocation=()" },
];

/** The request header that carries the nonce to the layout (Next reads it from the request's own policy). */
export const NONCE_HEADER = "x-nonce";

/**
 * The Content-Security-Policy of one response: the base policy, and a script runs only if it carries this request's
 * nonce or was loaded by one that does (`'strict-dynamic'`). Next.js puts the nonce on its own scripts when the
 * request it renders carries this policy. Development adds `'unsafe-eval'`, which React's dev tooling needs.
 */
export function contentSecurityPolicy(nonce: string, development: boolean): string {
  const script = `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ""}`;
  return `${BASE_POLICY}; ${script}`;
}

/** A fresh nonce for one request: 128 random bits, base64. */
export function newNonce(): string {
  return btoa(String.fromCodePoint(...crypto.getRandomValues(new Uint8Array(16))));
}
