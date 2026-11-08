/**
 * One flag decides whether this deployment is open to search engines: `APP_SEARCH_INDEXING=off` (every dev and preview
 * deployment) closes it — `X-Robots-Tag: noindex, nofollow, noarchive` on every response (src/proxy.ts), robots.txt
 * `Disallow: /` with no sitemap, noindex in the pages' metadata, and the « development environment » line under the
 * footer. Production flips it by configuration alone. Read at request time on the server.
 */
export const NOINDEX_HEADER = "noindex, nofollow, noarchive";
export const CLOSED_ROBOTS = "User-agent: *\nDisallow: /\n";

export function indexable(env: Record<string, string | undefined> = process.env): boolean {
  // A code-name address (www.orazaka.com) is closed by its name too: the public face is the commercial domain (wave 1, krizaka-internal#2630).
  return !developmentDeployment(env) && !isCodeNameAddress(hostOf(env.APP_PUBLIC_URL));
}

/**
 * A development or preview deployment: the flag `APP_SEARCH_INDEXING=off`, or an address that is dev.* by its name. It
 * shows the « development version » notice and the code-name line; a code-name host that is live (www.orazaka.com) is
 * closed to search engines but shows neither.
 */
export function developmentDeployment(env: Record<string, string | undefined> = process.env): boolean {
  // A deployment whose own address is dev.* is closed by its name, whatever the flag says (orazaka-web-client#262).
  return env.APP_SEARCH_INDEXING?.trim().toLowerCase() === "off" || isDevAddress(hostOf(env.APP_PUBLIC_URL), env);
}

/** The products' code-name hosts (www.orazaka.com...): a product's public address is its commercial domain, so these are never indexed. */
const CODE_NAME_HOST = /^(www\.)?(orazaka|yomizaka|akinaza)\.com$/;
export const isCodeNameAddress = (host: string | null | undefined): boolean =>
  CODE_NAME_HOST.test((host ?? "").trim().toLowerCase().replace(/:\d+$/, ""));

/* --- Two addresses, one app (epic krizaka/.github#119, krizaka-com AGENTS section 2.4) ---
   www.orazaka.com is the public address (SITE_URL, indexed). dev.orazaka.com (APP_DEV_HOST) is the development address:
   every HTML page goes to www with a 308, robots.txt is Disallow, and only what is called keeps serving there - the BFF
   and auth (/api/*), /.well-known (app links) and the health check - all noindex. The engine's own API (/edge) is the
   platform ingress, not this app: it answers on both hosts. */
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
