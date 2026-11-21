/** @jest-environment node */
// The console's BFF relays the law corpus' manual import (ADR-089 §8) as it came — multipart, its boundary intact —
// and a JSON write as JSON, with the operator's token.
import { NextRequest } from "next/server";
import { backendToken } from "@/core/auth/backend-token";
import { SESSION_COOKIE } from "@/console/core/auth/session-cookie";
import { POST } from "@/app/console/api/v1/[...path]/route";

jest.mock("@/core/auth/backend-token", () => ({ backendToken: jest.fn().mockResolvedValue("operator-jwt") }));
jest.mock("@/console/core/auth/auth-options", () => ({ authOptions: { secret: "console-seal" } }));

const params = (path: string[]) => ({ params: Promise.resolve({ path }) });

function upstream() {
  const fetchMock = jest
    .fn()
    .mockResolvedValue(new Response('{"stored":1}', { status: 200, headers: { "content-type": "application/json" } }));
  global.fetch = fetchMock as never;
  return fetchMock;
}

describe("console BFF: request bodies", () => {
  it("relays a multipart upload with its boundary", async () => {
    const fetchMock = upstream();
    const form = new FormData();
    form.append("file", new Blob(["<loi/>"], { type: "application/xml" }), "ccq.xml");
    form.append("datasetUrl", "https://www.donneesquebec.ca/ccq.xml");
    const request = new NextRequest("https://admin.sovkora.com/api/v1/admin/law/imports", {
      method: "POST",
      body: form,
    });
    const type = request.headers.get("content-type");
    const res = await POST(request, params(["admin", "law", "imports"]));
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8088/api/v1/admin/law/imports");
    expect(init.headers).toEqual({ "Content-Type": type, Authorization: "Bearer operator-jwt" });
    // The console's own session cookie and key, never the public site's.
    expect(backendToken).toHaveBeenCalledWith(request, {
      cookieName: SESSION_COOKIE,
      secret: "console-seal",
    });
    const relayed = await new Request("http://x", {
      method: "POST",
      body: init.body,
      headers: { "content-type": type ?? "" },
    }).formData();
    expect(relayed.get("datasetUrl")).toBe("https://www.donneesquebec.ca/ccq.xml");
  });

  it("relays a JSON write as JSON", async () => {
    const fetchMock = upstream();
    await POST(
      new NextRequest("https://admin.sovkora.com/api/v1/admin/law/proposals/p1/reject", {
        method: "POST",
        body: '{"reason":"wrong article"}',
        headers: { "content-type": "application/json" },
      }),
      params(["admin", "law", "proposals", "p1", "reject"]),
    );
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(init.body).toBe('{"reason":"wrong article"}');
  });
});
