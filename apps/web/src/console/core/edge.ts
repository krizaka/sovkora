/**
 * @file edge.ts
 * @description The proxy's side of the operator console (admin.orazaka.com), served by this app on its own host.
 * In order: the network gate (any address not in `AUTH_ALLOWED_IPS` gets 403, sign-in page included — only
 * `robots.txt`, which says "Disallow: /", is outside it), the sign-in gate (a page without the console's own session
 * cookie goes to `/login`; a closed console answers 404 on the BFF routes), then the rewrite under `/console`.
 * Every answer says noindex. The public host never gets here (`host.ts`).
 */
import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { SESSION_COOKIE } from "@/console/core/auth/session-cookie";
import { readAuthFlags } from "@/console/core/config/auth-flags";
import { gate } from "@/console/core/config/gate";
import { clientIp, ipAllowed, parseAllowList } from "@/console/core/config/ip-allow";

const NOINDEX = "noindex, nofollow";

function noindex(response: NextResponse): NextResponse {
  response.headers.set("x-robots-tag", NOINDEX);
  return response;
}

/** `rewritePath` is the `/console/…` page this admin-host request is served by. */
export function consoleResponse(request: NextRequest, rewritePath: string): NextResponse {
  const { pathname, search } = request.nextUrl;
  const rewrite = () => noindex(NextResponse.rewrite(new URL(`${rewritePath}${search}`, request.url)));
  if (pathname === "/robots.txt") return rewrite();

  const production = process.env.NODE_ENV === "production";
  if (!ipAllowed(clientIp(request.headers, production), parseAllowList(process.env.AUTH_ALLOWED_IPS), production)) {
    return noindex(new NextResponse("Forbidden", { status: 403 }));
  }

  const hasSession = request.cookies.has(SESSION_COOKIE) || request.cookies.has(`__Secure-${SESSION_COOKIE}`);
  const decision = gate(pathname, hasSession, readAuthFlags());
  if (decision === "not-found") {
    return noindex(
      NextResponse.json(
        { error: "Sovkora Operations is in development — sign-in is closed on this site" },
        { status: 404 },
      ),
    );
  }
  if (decision === "login") {
    // The public address of the console, not the platform's internal one.
    const host = request.headers.get("host");
    const proto = request.headers.get("x-forwarded-proto") ?? request.nextUrl.protocol.replace(":", "");
    const login = new URL("/login", host ? `${proto}://${host}` : request.url);
    if (pathname !== "/") login.searchParams.set("callbackUrl", `${pathname}${search}`);
    return noindex(NextResponse.redirect(login));
  }
  return rewrite();
}
