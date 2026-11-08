/** @jest-environment node */
import { proxyEventStream } from "@/core/server/sse-proxy";
import type { NextRequest } from "next/server";

jest.mock("next-auth/next", () => ({ getServerSession: jest.fn() }));
jest.mock("@/core/auth/auth-options", () => ({ authOptions: {} }));

import { getServerSession } from "next-auth/next";

const request = (body: string) => ({ json: async () => JSON.parse(body) }) as unknown as NextRequest;
const proxy = {
  path: "/api/v1/code",
  payload: ({ prompt, model }: { prompt: string; model?: string }) => ({ prompt, model }),
  log: "Code Stream",
};

describe("proxyEventStream", () => {
  afterEach(() => jest.restoreAllMocks());

  it("refuses a body that is not JSON, and a missing prompt, before looking at the session", async () => {
    expect((await proxyEventStream(request("not json"), proxy)).status).toBe(400);
    expect((await proxyEventStream(request("{}"), proxy)).status).toBe(400);
    expect(getServerSession).not.toHaveBeenCalled();
  });

  it("requires a session", async () => {
    (getServerSession as jest.Mock).mockResolvedValue(null);
    expect((await proxyEventStream(request('{"prompt":"hi"}'), proxy)).status).toBe(401);
  });

  it("forwards the prompt with the user as bearer and pipes the event stream back", async () => {
    (getServerSession as jest.Mock).mockResolvedValue({ user: { id: "u1" } });
    const upstream = new Response("data: x\n\n", { status: 200 });
    const fetchMock = jest.spyOn(globalThis, "fetch").mockResolvedValue(upstream);
    const res = await proxyEventStream(request('{"prompt":"hi","model":"m"}'), proxy);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/v1\/code$/);
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer u1");
    expect(JSON.parse(init.body as string)).toEqual({ prompt: "hi", model: "m" });
    expect(res.headers.get("content-type")).toBe("text/event-stream");
    expect(await res.text()).toBe("data: x\n\n");
  });

  it("answers the gateway's status when it refuses, and 500 when it is unreachable", async () => {
    (getServerSession as jest.Mock).mockResolvedValue({ user: { id: "u1" } });
    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response("no", { status: 503, statusText: "Unavailable" }));
    expect((await proxyEventStream(request('{"prompt":"hi"}'), proxy)).status).toBe(503);
    jest.spyOn(console, "error").mockImplementation(() => {});
    jest.spyOn(globalThis, "fetch").mockRejectedValueOnce(new Error("down"));
    const res = await proxyEventStream(request('{"prompt":"hi"}'), proxy);
    expect(res.status).toBe(500);
    expect(await res.json()).toEqual({ error: "down" });
  });
});
