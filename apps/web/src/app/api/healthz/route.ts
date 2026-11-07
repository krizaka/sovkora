/**
 * @file route.ts
 * @description Liveness of the web app for the probe of the Dev preview standard (`site.healthy`): 200 and the version
 * of the build. It touches no backend — the edge's own health is the edge's concern.
 */

import { NextResponse } from "next/server";
import { APP_VERSION } from "@/core/config/version";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({ status: "ok", version: APP_VERSION }, { headers: { "Cache-Control": "no-store" } });
}
