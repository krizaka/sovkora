/**
 * One flag decides whether this deployment is open to search engines: `APP_SEARCH_INDEXING=off` (every dev and preview
 * deployment) closes it — `X-Robots-Tag: noindex, nofollow, noarchive` on every response (src/proxy.ts), robots.txt
 * `Disallow: /` with no sitemap, noindex in the pages' metadata, and the « development environment » line under the
 * footer. Production flips it by configuration alone. Read at request time on the server.
 */
export const NOINDEX_HEADER = "noindex, nofollow, noarchive";
export const CLOSED_ROBOTS = "User-agent: *\nDisallow: /\n";

export function indexable(env: Record<string, string | undefined> = process.env): boolean {
  // A dev.* address is closed by its name; a code-name host (orazaka.com…) is never indexed because the commercial domain is the public face (wave 1, krizaka-internal#2630).
  return !developmentDeployment(env) && !isCodeNameAddress(hostOf(env.APP_PUBLIC_URL));
}

/**
 * A development or preview deployment: the flag `APP_SEARCH_INDEXING=off`, or an address that is dev.* by its name
 * (dev.sovkora.com falls here — it is the development address, closed to search engines by its dev.* prefix).
 * Shows the « development version » notice and the code-name line.
 */
export function developmentDeployment(env: Record<string, string | undefined> = process.env): boolean {
  // A deployment whose own address is dev.* is closed by its name, whatever the flag says (orazaka-web-client#262).
  return env.APP_SEARCH_INDEXING?.trim().toLowerCase() === "off" || isDevAddress(hostOf(env.APP_PUBLIC_URL), env);
}

/** The products' code-name hosts: a product's public address is its commercial domain, so these are never indexed. */
const CODE_NAME_HOST = /^(www\.)?(orazaka|yomizaka|akinaza)\.com$/;
export const isCodeNameAddress = (host: string | null | undefined): boolean =>
  CODE_NAME_HOST.test((host ?? "").trim().toLowerCase().replace(/:\d+$/, ""));

/* --- dev.* address (dev.sovkora.com) is the development address (epic krizaka/.github#119, krizaka-com AGENTS section 2.4) ---
   dev.sovkora.com is the development address (APP_PUBLIC_URL), closed to search engines by its dev.* prefix.
   The dev-host mechanism (APP_DEV_HOST) is kept for another dev address and is inert when unset; every HTML page on
   APP_DEV_HOST goes to APP_PUBLIC_URL with a 308, robots.txt is Disallow, and only /api/*, /.well-known and /healthz
   keep serving — all noindex. The engine's own API (/edge) is the platform ingress, not this app. */
const bare = (host: string | null | undefined) => (host ?? "").trim().toLowerCase().replace(/:\d+$/, "");
export const isDevHost = (
  host: string | null | undefined,
  env: Record<string, string | undefined> = process.env,
): boolean => !!env.APP_DEV_HOST?.trim() && bare(host) === bare(env.APP_DEV_HOST);
/** A dev.* address is never indexed — by its name, not by configuration: the one the app redirects (APP_DEV_HOST) and any other. */
export const isDevAddress = (
  host: string | null | undefined,
  env: Record<string, string | undefined> = process.env,
): boolean => bare(host).startsWith("dev.") || isDevHost(host, env);
function hostOf(url: string | undefined): string {
  try {
    return url ? new URL(url).host : "";
  } catch {
    return "";
  }
}

const DEV_KEEPS = ["/api/", "/.well-known/"];
const DEV_KEEPS_EXACT = ["/robots.txt", "/healthz"];
/** The www address a dev-host request goes to (308), or null when the dev host keeps serving it. Only GET and HEAD are pages. */
export function devRedirect(method: string, pathname: string, search: string, publicUrl: string): string | null {
  if (method !== "GET" && method !== "HEAD") return null;
  if (DEV_KEEPS_EXACT.includes(pathname) || DEV_KEEPS.some((p) => pathname.startsWith(p))) return null;
  return `${publicUrl.replace(/\/$/, "")}${pathname}${search}`;
}

/** An address that no configuration opens to search engines: a dev.* address or a product's code-name host. */
export const neverIndexed = (host: string | null | undefined): boolean => isDevAddress(host) || isCodeNameAddress(host);
