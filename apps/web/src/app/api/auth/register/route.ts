/**
 * @file route.ts
 * @description Sign-up endpoint of the BFF. Closed server-side while `AUTH_SIGNUP_ENABLED` is off (the default): 403, no
 * backend call (Dev preview: `site.signup-closed`). Open, it relays the body to the edge's `/api/v1/auth/register`.
 */

import { NextResponse } from "next/server";
import { parseFlag } from "@/core/config/auth-flags";

export async function POST(req: Request) {
  if (!parseFlag(process.env.AUTH_SIGNUP_ENABLED)) {
    return NextResponse.json({ error: "Sign-up is closed: Sovkora is in development" }, { status: 403 });
  }
  const edgeUrl = process.env.ROUTER_URL || process.env.GATEWAY_URL || "http://localhost:8088";
  try {
    const response = await fetch(`${edgeUrl}/api/v1/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: await req.text(),
    });
    const text = await response.text();
    return new NextResponse(text || null, {
      status: response.status,
      headers: { "Content-Type": response.headers.get("content-type") || "application/json" },
    });
  } catch {
    return NextResponse.json({ error: "Sign-up is unavailable" }, { status: 502 });
  }
}
