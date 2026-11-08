/**
 * @file route.ts
 * @description Catch-all API Route Handler that acts as a server-side BFF (Backend-for-Frontend) proxy
 * for all REST endpoints under /api/v1/*.
 *
 * This handler validates the client session, injects the session JWT the identity service
 * issued at login as the Bearer token, and proxies the request to the Sovkora edge (:8088),
 * whose route table fans /api/v1/* out to the owning service (conversation, identity, …).
 */

import { NextResponse } from "next/server";
import { getServerSession } from "next-auth/next";
import { authOptions } from "@/core/auth/auth-options";

async function proxyRequest(req: Request, segments: string[], method: string) {
  const edgeUrl = process.env.ROUTER_URL || process.env.GATEWAY_URL || "http://localhost:8088";

  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: "Unauthorized: Missing active security context" }, { status: 401 });
    }

    const userId = session.user.id;
    const pathStr = segments.join("/");
    const { search } = new URL(req.url);
    const targetUrl = `${edgeUrl}/api/v1/${pathStr}${search}`;

    let body: BodyInit | undefined = undefined;
    if (["POST", "PUT", "PATCH"].includes(method)) {
      try {
        const reqContentType = req.headers.get("content-type") || "";
        if (reqContentType.includes("multipart/form-data")) {
          body = await req.arrayBuffer();
        } else {
          body = await req.text();
        }
      } catch {
        // Request has no body or reading failed
      }
    }

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Authorization: `Bearer ${userId}`,
    };

    // Forward incoming content type if present
    const reqContentType = req.headers.get("content-type");
    if (reqContentType) {
      headers["Content-Type"] = reqContentType;
    }

    const response = await fetch(targetUrl, {
      method,
      headers,
      body,
    });

    const contentType = response.headers.get("content-type") || "";

    // 204/205/304 carry no body, and a Response refuses one: relaying "" with these statuses threw
    // "Invalid response status code 204" and turned every approve / uninstall into a 500.
    if ([204, 205, 304].includes(response.status)) {
      return new NextResponse(null, { status: response.status });
    }

    // Server-Sent Events (e.g. /api/v1/jobs/stream): pipe the body straight
    // through. Buffering a long-lived stream with .text() never resolves, so the
    // browser EventSource (useJobSSE) would never receive job-status/-progress
    // events and tasks stay stuck "in progress". Mirrors the dedicated chat
    // streaming route.
    if (contentType.includes("text/event-stream")) {
      return new NextResponse(response.body, {
        status: response.status,
        headers: {
          "Content-Type": "text/event-stream",
          "Cache-Control": "no-cache, no-transform",
          Connection: "keep-alive",
          "X-Accel-Buffering": "no",
        },
      });
    }

    if (contentType.includes("application/json")) {
      const data = await response.json();
      return NextResponse.json(data, { status: response.status });
    } else {
      const data = await response.text();
      return new NextResponse(data, {
        status: response.status,
        headers: { "Content-Type": contentType },
      });
    }
  } catch (error: unknown) {
    console.error(`[BFF Proxy] Error proxying ${method} to /api/v1/${segments.join("/")}:`, error);
    const message = error instanceof Error ? error.message : "BFF API Proxy Error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}

export async function POST(req: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const resolvedParams = await params;
  return proxyRequest(req, resolvedParams.path, "POST");
}

export async function GET(req: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const resolvedParams = await params;
  return proxyRequest(req, resolvedParams.path, "GET");
}

export async function PUT(req: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const resolvedParams = await params;
  return proxyRequest(req, resolvedParams.path, "PUT");
}

export async function DELETE(req: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const resolvedParams = await params;
  return proxyRequest(req, resolvedParams.path, "DELETE");
}

export async function PATCH(req: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const resolvedParams = await params;
  return proxyRequest(req, resolvedParams.path, "PATCH");
}
