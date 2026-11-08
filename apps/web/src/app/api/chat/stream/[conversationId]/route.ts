/**
 * @file route.ts
 * @description Next.js API route that acts as a server-side BFF (Backend-for-Frontend) proxy
 * for token-streaming chat requests. It avoids browser-side CORS issues, prevents exposing backend ports,
 * and secures credential storage by forwarding requests to the Gateway service.
 *
 * This endpoint runs strictly server-side and requires an active authenticated session.
 *
 * @see {@link http://localhost:8080/api/v1/chat/stream/{conversationId}} - Upstream gateway SSE endpoint.
 */

import type { NextRequest } from "next/server";
import { proxyEventStream } from "@/core/server/sse-proxy";

/**
 * Handles incoming POST requests: the session check, the bearer and the event-stream piping are shared with the code route
 * (see `proxyEventStream`).
 *
 * @param req - The NextRequest object representing the incoming HTTP request.
 * @param context - The context object containing route parameters.
 * @param context.params - A promise resolving to parameters containing the conversationId.
 * @returns A promise resolving to a Response object containing the active EventStream or an HTTP error code.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ conversationId: string }> }) {
  const { conversationId } = await params;
  return proxyEventStream(req, {
    path: `/api/v1/chat/stream/${conversationId}`,
    payload: ({ prompt, model, body }) => ({ prompt, assetIds: (body.assetIds as string[]) || [], model }),
    log: "Stream",
  });
}
