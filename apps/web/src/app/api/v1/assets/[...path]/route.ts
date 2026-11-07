/**
 * @file route.ts
 * @description BFF proxy for a job's media, served under /api/v1/assets/{jobId}/{filename}.
 *
 * A browser `<img src>` / `<video src>` cannot carry an `Authorization` header, so this
 * server-side route attaches the session JWT and streams the bytes back.
 *
 * It takes **no** authorisation decision. The route it replaced built its target from
 * `/uploads/${segments.join("/")}`, where the first segment was the owner — and never compared it
 * to the session, so any signed-in user could read any other's media by editing one segment. The
 * owner is now resolved server-side from the job record; the path here only names a file.
 *
 * A dedicated route rather than the generic `/api/v1/[...path]` one: that proxy buffers non-SSE
 * responses, which would corrupt binary payloads and hold a whole video in memory per viewer.
 * Here the body is piped and `Range` is forwarded so `<video>` seeking keeps working (206).
 */

import { NextResponse } from "next/server";
import { getServerSession } from "next-auth/next";
import { authOptions } from "@/core/auth/auth-options";

const ROUTER_URL = process.env.ROUTER_URL || process.env.GATEWAY_URL || "http://localhost:8080";

/** Upstream response headers safe to forward verbatim to the browser. */
const FORWARDED_HEADERS = [
  "content-type",
  "content-length",
  "content-range",
  "accept-ranges",
  "last-modified",
  "etag",
  "cache-control",
];

async function proxyAsset(req: Request, segments: string[], method: "GET" | "HEAD") {
  const session = await getServerSession(authOptions);
  // `session.user.id` carries the signed HS256 session JWT, not a user id — see auth-options.ts.
  const sessionToken = session?.user?.id;
  if (!sessionToken) {
    return NextResponse.json({ error: "Unauthorized: Missing active security context" }, { status: 401 });
  }

  const { search } = new URL(req.url);
  const targetUrl = `${ROUTER_URL}/api/v1/assets/${segments.join("/")}${search}`;

  const headers: Record<string, string> = {
    Authorization: `Bearer ${sessionToken}`,
  };
  const range = req.headers.get("range");
  if (range) {
    headers.Range = range;
  }

  try {
    const response = await fetch(targetUrl, { method, headers });

    const responseHeaders = new Headers();
    for (const name of FORWARDED_HEADERS) {
      const value = response.headers.get(name);
      if (value) {
        responseHeaders.set(name, value);
      }
    }
    // The backend sends `no-store`; this is the floor if it ever stops. Authenticated media must
    // not survive in any cache a second viewer could reach.
    if (!responseHeaders.has("cache-control")) {
      responseHeaders.set("Cache-Control", "private, no-store");
    }

    return new NextResponse(method === "HEAD" ? null : response.body, {
      status: response.status,
      headers: responseHeaders,
    });
  } catch (error: unknown) {
    console.error(`[BFF Assets] Error proxying ${method} ${segments.join("/")}:`, error);
    const message = error instanceof Error ? error.message : "BFF Assets Proxy Error";
    return NextResponse.json({ error: message }, { status: 502 });
  }
}

export async function GET(req: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  return proxyAsset(req, path, "GET");
}

export async function HEAD(req: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  return proxyAsset(req, path, "HEAD");
}
