/**
 * @file proxy.ts
 * @description Next.js Proxy (ex-Middleware) for authentication gating.
 * Redirects unauthenticated users to /login for protected page routes.
 * Uses the next-auth session token cookie to determine auth status server-side,
 * avoiding skeleton flash on protected pages.
 *
 * Next.js 16 renamed the `middleware` file convention to `proxy`; the exported
 * entrypoint is now named `proxy` (same request/response contract as before).
 */

import { sessionHint } from "@krizaka/auth";
import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { authClosed, isBackendPath, readAuthFlags } from "@/core/config/auth-flags";
import {
  CLOSED_ROBOTS,
  devRedirect,
  indexable,
  neverIndexed,
  isDevAddress,
  isDevHost,
  NOINDEX_HEADER,
} from "@/core/config/indexing";
import { isAppPath, PUBLIC_PREFIXES } from "@/core/config/app-routes";
import { SIGNED_OUT_REDIRECTS } from "@/core/config/redirects";
import { LOCALE_HEADER, splitLocalePrefix } from "@/core/i18n/locale-negotiation";
import { SITE_URL } from "@/app/_seo/seo";
import { consoleResponse } from "@/console/core/edge";
import { isAdminHost, routeFor } from "@/console/core/config/host";
/** The next-auth cookie of the session, by the name this deployment uses (`__Secure-` on https); it is only read, as a hint, never trusted (krizaka-ui#827). */
const SESSION_COOKIES = ["__Secure-next-auth.session-token", "next-auth.session-token"] as const;
const sessionCookieOf = (request: NextRequest) =>
  SESSION_COOKIES.find((name) => request.cookies.has(name)) ?? SESSION_COOKIES[1];

/** The platform's liveness probe: answered on every host, before any gate. */
const HEALTH_PATH = "/api/healthz";

/**
 * Checks whether a given pathname matches any public (unauthenticated) route prefix.
 */
function isPublicPath(pathname: string): boolean {
  // `/` itself is public: it renders the landing page for a visitor and the dashboard for a session
  // (src/app/page.tsx). It cannot be a prefix — every path starts with "/".
  return pathname === "/" || PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

function route(request: NextRequest) {
  // `/fr/…` and `/en/…` are the same pages with the language named by the address (Loi 96): they are gated and served as `/…`.
  const { locale, path: pathname } = splitLocalePrefix(request.nextUrl.pathname);
  const next = () => {
    if (!locale) {
      return NextResponse.next();
    }
    const target = request.nextUrl.clone();
    target.pathname = pathname;
    // The page's `<title>` and Open Graph are written in the address's language (app/layout.tsx reads this header).
    const headers = new Headers(request.headers);
    headers.set(LOCALE_HEADER, locale);
    return NextResponse.rewrite(target, { request: { headers } });
  };

  // Both entry points closed (AUTH_LOGIN_ENABLED / AUTH_SIGNUP_ENABLED false): nothing is proxied to the backend.
  if (authClosed(readAuthFlags()) && isBackendPath(pathname)) {
    return NextResponse.json({ error: "Sovkora is in development — sign-in is closed on this site" }, { status: 404 });
  }

  // Allow public routes, API routes, and static assets through
  if (isPublicPath(pathname)) {
    return next();
  }

  // Check for next-auth session token (works for both secure and dev cookies)
  const sessionToken =
    request.cookies.get("next-auth.session-token")?.value ||
    request.cookies.get("__Secure-next-auth.session-token")?.value;

  if (!sessionToken) {
    // An address that is not a page of the app does not exist: Next answers 404 (src/app/not-found.tsx), not the login form.
    if (!isAppPath(pathname) && !SIGNED_OUT_REDIRECTS.some((r) => r.source === pathname)) return next();
    const moved = SIGNED_OUT_REDIRECTS.find((r) => r.source === pathname);
    if (moved) return NextResponse.redirect(new URL(moved.destination, request.url), 308);
    const loginUrl = new URL("/login", request.url);
    return NextResponse.redirect(loginUrl);
  }

  return next();
}

/**
 * Two sites, one app. admin.orazaka.com is the operator console: its requests are gated (network, then sign-in) and
 * rewritten under the internal `/console` tree, and every answer says noindex. On any other host `/console` is a 404.
 * The rest is the public site; a deployment closed to search engines (APP_SEARCH_INDEXING=off) says noindex on every
 * response — pages, redirects, the BFF.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const host = request.headers.get("host");
  // dev.orazaka.com (APP_DEV_HOST): robots.txt closes everything, a page goes to www (308); the BFF, auth and
  // /.well-known keep serving - all noindex (src/core/config/indexing.ts).
  if (isDevHost(host)) {
    const closed = (r: NextResponse) => {
      r.headers.set("x-robots-tag", NOINDEX_HEADER);
      return r;
    };
    if (pathname === "/robots.txt")
      return closed(
        new NextResponse(CLOSED_ROBOTS, { status: 200, headers: { "content-type": "text/plain; charset=utf-8" } }),
      );
    const to = devRedirect(request.method, pathname, request.nextUrl.search, process.env.APP_PUBLIC_URL ?? SITE_URL);
    if (to) return closed(NextResponse.redirect(to, 308));
    if (pathname !== HEALTH_PATH) return closed(route(request));
    return closed(NextResponse.next());
  }
  let response: NextResponse;
  if (pathname === HEALTH_PATH) {
    response = NextResponse.next();
  } else {
    const hostRoute = routeFor(host, pathname);
    if (pathname === "/robots.txt" && (isAdminHost(host) || isDevAddress(host))) {
      // The console and any dev.* address are never indexed: said here, before the network gate (a crawler must read it).
      return new NextResponse("User-agent: *\nDisallow: /\n", {
        status: 200,
        headers: { "content-type": "text/plain; charset=utf-8", "x-robots-tag": NOINDEX_HEADER },
      });
    }
    switch (hostRoute.kind) {
      case "notFound":
        response = new NextResponse("Not found", { status: 404 });
        if (isAdminHost(host)) response.headers.set("x-robots-tag", NOINDEX_HEADER);
        break;
      case "rewrite":
        return consoleResponse(request, hostRoute.path);
      default:
        response = route(request);
    }
  }
  if (!indexable() || neverIndexed(host)) response.headers.set("x-robots-tag", NOINDEX_HEADER);
  // The bar's readable hint follows the session cookie, so a signed-in person never sees « Sign in » while the session is read (krizaka-ui#827).
  return sessionHint(response, request, {
    cookie: sessionCookieOf(request),
    secure: process.env.NODE_ENV === "production",
  });
}

export const config = {
  /**
   * Run on pages and on the BFF routes (a closed site answers 404 there) — exclude static assets and internals.
   */
  matcher: ["/((?!_next/static|_next/image|_next/data|favicon).*)"],
};
