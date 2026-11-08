/**
 * @file route.ts
 * @description Unauthenticated BFF proxy for email verification. Verification happens before
 * login (no session yet), so — unlike the catch-all `/api/v1` proxy — this route does not require
 * a session. It forwards the `{ token }` body to the Router's `/api/v1/auth/verify` (a permitAll
 * endpoint). Replaces the former GraphQL `verifyEmail` mutation (REST-only consolidation).
 */

import { NextResponse } from "next/server";

const ROUTER_URL = process.env.ROUTER_URL || process.env.GATEWAY_URL || "http://localhost:8080";

export async function POST(req: Request) {
  try {
    const body = await req.text();
    const response = await fetch(`${ROUTER_URL}/api/v1/auth/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
    });
    const text = await response.text();
    return new NextResponse(text || null, {
      status: response.status,
      headers: {
        "Content-Type": response.headers.get("content-type") || "application/json",
      },
    });
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : "BFF verify proxy error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
