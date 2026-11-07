import { ApiError, apiRequest, edgeUrl } from "../api";

function respondWith(body: string, status = 200): jest.Mock {
  const mock = jest.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(body),
  });
  (globalThis as { fetch: unknown }).fetch = mock;
  return mock;
}

describe("edgeUrl", () => {
  it("joins a path whether or not it is already rooted", () => {
    expect(edgeUrl("/api/v1/studios")).toMatch(/\/api\/v1\/studios$/);
    expect(edgeUrl("api/v1/studios")).toMatch(/\/api\/v1\/studios$/);
  });

  it("never doubles the separator", () => {
    expect(edgeUrl("/api")).not.toContain("//api");
  });
});

describe("apiRequest", () => {
  it("attaches the bearer when there is a session", async () => {
    const fetchMock = respondWith("{}");

    await apiRequest("/api/v1/profile", { token: "jwt-1" });

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer jwt-1");
  });

  it("sends no Authorization header on the public endpoints", async () => {
    const fetchMock = respondWith("{}");

    await apiRequest("/api/v1/auth/login", { method: "POST", body: { email: "a@b.c" } });

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers).not.toHaveProperty("Authorization");
  });

  it("omits the body entirely on a GET rather than sending 'undefined'", async () => {
    const fetchMock = respondWith("[]");

    await apiRequest("/api/v1/studios", { token: "jwt-1" });

    expect(fetchMock.mock.calls[0][1]).not.toHaveProperty("body");
  });

  it("returns the parsed JSON", async () => {
    respondWith('{"username":"ada"}');

    await expect(apiRequest("/api/v1/profile", { token: "t" })).resolves.toEqual({
      username: "ada",
    });
  });

  it("treats an empty 204 as undefined instead of failing to parse it", async () => {
    respondWith("", 204);

    await expect(apiRequest("/api/v1/studios/x", { token: "t" })).resolves.toBeUndefined();
  });

  it("keeps the status on the error, so 401 stays distinguishable from 500", async () => {
    respondWith('{"error":"Unauthorized"}', 401);

    // A caller that cannot tell these apart ends up telling a signed-out user the network is down.
    await expect(apiRequest("/api/v1/profile", { token: "stale" })).rejects.toMatchObject({
      name: "ApiError",
      status: 401,
      message: "Unauthorized",
    });
  });

  it("surfaces the service's own message when it left one", async () => {
    respondWith('{"message":"Studio introuvable"}', 404);

    await expect(apiRequest("/api/v1/studios/nope", { token: "t" })).rejects.toThrow("Studio introuvable");
  });

  it("falls back to the status when the body carries no message", async () => {
    respondWith("{}", 503);

    await expect(apiRequest("/api/v1/studios", { token: "t" })).rejects.toThrow("503");
  });

  it("is an ApiError, so callers can branch on it", async () => {
    respondWith("{}", 500);

    await expect(apiRequest("/api/v1/studios", { token: "t" })).rejects.toBeInstanceOf(ApiError);
  });
});
