import { readFileSync } from "fs";
import { join } from "path";
import { ApiError, apiRequest, DEV_EDGE_URL } from "../api";

// krizaka/orazaka-mobile-client#39: www.orazaka.com is the Next.js BFF (a cookie session). The edge a bearer client
// talks to is published on the same host under /edge. Pointing the app at the bare host answered
// 401 "Missing active security context" to every sign-in.
const root = join(__dirname, "../../..");
const fixture = (name: string): Record<string, unknown> =>
  JSON.parse(readFileSync(join(root, "src/__fixtures__/api", name), "utf8")) as Record<string, unknown>;
const localDemo = fixture("auth-login.local-demo.json");
const devEdge = fixture("auth-login.dev-edge.json");
const eas = JSON.parse(readFileSync(join(root, "eas.json"), "utf8")) as {
  build: Record<string, { env?: Record<string, string> }>;
};

describe("the dev edge address (the baked default and the legacy address; the manifest of krizaka.com says where it is now)", () => {
  it("is the public /edge of www.orazaka.com, never the BFF root", () => {
    expect(DEV_EDGE_URL).toBe("https://www.orazaka.com/edge");
  });

  it.each(["development", "preview", "production", "simulator"])(
    "is not baked into the EAS %s profile: discovery decides (an override would win over it)",
    (profile) => {
      expect(Object.keys(eas.build[profile].env ?? {})).not.toContain("EXPO_PUBLIC_EDGE_URL");
      expect(JSON.stringify(eas.build[profile])).not.toContain("orazaka.com");
    },
  );
});

describe("the login contract", () => {
  it("is the same shape on the local stack and on the dev edge (shared fixtures)", () => {
    expect(Object.keys(devEdge).sort()).toEqual(Object.keys(localDemo).sort());
    expect(Object.keys(devEdge).sort()).toEqual(["activeInterceptions", "authorities", "email", "token", "username"]);
  });
});

describe("a bearer client that reached the website's BFF", () => {
  it("is told so instead of being told its password is wrong", async () => {
    (globalThis as { fetch: unknown }).fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 401,
      text: () => Promise.resolve('{"error":"Unauthorized: Missing active security context"}'),
    });

    const error = await apiRequest("/api/v1/auth/login", { method: "POST", body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).message).toMatch(/website, not the edge/);
    expect((error as ApiError).message).toContain(DEV_EDGE_URL);
  });
});
