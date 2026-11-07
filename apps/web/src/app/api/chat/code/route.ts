import type { NextRequest } from "next/server";
import { proxyEventStream } from "@/core/server/sse-proxy";

export function POST(req: NextRequest) {
  return proxyEventStream(req, {
    path: "/api/v1/code",
    payload: ({ prompt, model }) => ({ prompt, model }),
    log: "Code Stream",
  });
}
