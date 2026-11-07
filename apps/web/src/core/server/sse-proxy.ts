import type { NextRequest } from "next/server";
import { getServerSession } from "next-auth/next";
import { authOptions } from "@/core/auth/auth-options";

export interface SseProxy {
  /** Gateway path after the base URL, e.g. `/api/v1/code`. */
  path: string;
  /** The JSON sent upstream, from the prompt, the model and the whole client body. */
  payload: (parts: {
    prompt: string;
    model: string | undefined;
    body: Record<string, unknown>;
  }) => Record<string, unknown>;
  /** How errors are named in the log and in the 500 answer (`SSE <log> Proxy Error`, `BFF SSE <log> Proxy Error`). */
  log: string;
}

/**
 * The BFF proxy of a token-streaming request, shared by the chat and code routes: validates the JSON and the prompt, requires the
 * session, forwards to the gateway with the user id as the bearer, and pipes the Server-Sent Events back without buffering.
 * Runs strictly server-side.
 */
export async function proxyEventStream(req: NextRequest, { path, payload, log }: SseProxy): Promise<Response> {
  let body: Record<string, unknown>;
  try {
    body = (await req.json()) as Record<string, unknown>;
  } catch {
    return new Response("Invalid JSON payload", { status: 400 });
  }
  const prompt = body.prompt as string;
  const model = body.model as string | undefined;

  if (!prompt) {
    return new Response("Missing prompt parameter", { status: 400 });
  }

  const session = await getServerSession(authOptions);
  if (!session?.user?.id) {
    return new Response("Unauthorized: Missing active security context", {
      status: 401,
    });
  }

  const userId = session.user.id;
  const gatewayUrl = process.env.ROUTER_URL || process.env.GATEWAY_URL || "http://localhost:8080";

  try {
    const response = await fetch(`${gatewayUrl}${path}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${userId}`,
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify(payload({ prompt, model, body })),
    });

    if (!response.ok) {
      return new Response(`Gateway stream error: ${response.statusText}`, {
        status: response.status,
      });
    }

    // Wrap the response body in a ReadableStream to stream chunks immediately without buffering
    const stream = new ReadableStream({
      async start(controller) {
        if (!response.body) {
          controller.close();
          return;
        }
        const reader = response.body.getReader();

        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            controller.enqueue(value);
          }
        } catch (err) {
          controller.error(err);
        } finally {
          controller.close();
        }
      },
    });

    return new Response(stream, {
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache, no-transform",
        Connection: "keep-alive",
        "X-Accel-Buffering": "no", // Prevent Nginx proxy buffering
      },
    });
  } catch (error: unknown) {
    console.error(`SSE ${log} Proxy Error:`, error);
    const message = error instanceof Error ? error.message : `BFF SSE ${log} Proxy Error`;
    return new Response(JSON.stringify({ error: message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
}
