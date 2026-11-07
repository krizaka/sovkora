/** @jest-environment node */
import { NextRequest } from "next/server";
import { sessionHint } from "@krizaka/auth";
import { proxy } from "@/proxy";
import { publicPaths } from "@/app/_seo/seo";

// @krizaka/auth ships ESM only (its own tests cover the cookie rules); here only what the proxy hands it is checked.
jest.mock("@krizaka/auth", () => ({ sessionHint: jest.fn((response: Response) => response) }), { virtual: true });

const call = (path: string, env: Record<string, string> = {}) => {
  const before = { ...process.env };
  Object.assign(process.env, env);
  try {
    return proxy(new NextRequest(`https://www.orazaka.com${path}`));
  } finally {
    delete process.env.AUTH_LOGIN_ENABLED;
    delete process.env.AUTH_SIGNUP_ENABLED;
    Object.assign(process.env, before);
  }
};

describe("proxy with the entry points closed (default)", () => {
  it.each(["/api/v1/profile", "/api/auth/session", "/api/chat/stream/1", "/uploads/a.png"])(
    "answers 404 on %s, no backend call",
    (path) => {
      expect(call(path).status).toBe(404);
    },
  );

  it.each(["/", "/docs", "/docs/core", "/desktop", "/login", "/register"])("lets %s through", (path) => {
    expect(call(path).headers.get("x-middleware-next")).toBe("1");
  });

  it("lets every page of the sitemap through — a public page never lands on the login wall", () => {
    for (const path of publicPaths())
      expect(`${path} ${String(call(path).headers.get("x-middleware-next"))}`).toBe(`${path} 1`);
  });

  it("sends a protected page to /login (the closed notice)", () => {
    const res = call("/jobs");
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toBe("https://www.orazaka.com/login");
  });
});

describe("Packs moved into the docs", () => {
  it("sends a visitor without a session from /packs to /docs/packs with a 308", () => {
    const res = call("/packs");
    expect(res.status).toBe(308);
    expect(res.headers.get("location")).toBe("https://www.orazaka.com/docs/packs");
  });

  it("keeps /packs the signed-in Plan & packs for a session", () => {
    const req = new NextRequest("https://www.orazaka.com/packs", { headers: { cookie: "next-auth.session-token=x" } });
    expect(proxy(req).headers.get("x-middleware-next")).toBe("1");
  });
});

describe("proxy with sign-in open", () => {
  it("lets the BFF routes through", () => {
    expect(call("/api/v1/profile", { AUTH_LOGIN_ENABLED: "true" }).headers.get("x-middleware-next")).toBe("1");
  });

  it.each(["/jobs", "/studios", "/settings", "/dashboard"])(
    "sends a signed-out visitor of %s to /login, and lets a session through",
    (path) => {
      const open = { AUTH_LOGIN_ENABLED: "true" };
      const res = call(path, open);
      expect(res.status).toBe(307);
      expect(res.headers.get("location")).toBe("https://www.orazaka.com/login");
      const before = { ...process.env };
      Object.assign(process.env, open);
      try {
        const req = new NextRequest(`https://www.orazaka.com${path}`, {
          headers: { cookie: "next-auth.session-token=x" },
        });
        expect(proxy(req).headers.get("x-middleware-next")).toBe("1");
      } finally {
        delete process.env.AUTH_LOGIN_ENABLED;
        Object.assign(process.env, before);
      }
    },
  );
});

describe("an address that does not exist (krizaka/orazaka#443)", () => {
  it.each(["/nope", "/jobz", "/studio", "/settings-old", "/a/b/c"])(
    "does not send a visitor of %s to the login page: Next answers 404",
    (path) => {
      const envs: Record<string, string>[] = [{}, { AUTH_LOGIN_ENABLED: "true" }];
      for (const env of envs) {
        const res = call(path, env);
        expect(res.headers.get("location")).toBeNull();
        expect(res.headers.get("x-middleware-next")).toBe("1");
      }
    },
  );

  it.each(["/jobs/42", "/studios/image-generation", "/settings/profile"])(
    "still sends a visitor of the app page %s to the login page",
    (path) => {
      expect(call(path, { AUTH_LOGIN_ENABLED: "true" }).headers.get("location")).toBe("https://www.orazaka.com/login");
    },
  );
});

// krizaka-ui#827: the bar's readable hint flag follows the next-auth cookie, so a signed-in person never sees « Sign in » while the session is read.
describe("the bar's session hint follows the next-auth cookie (krizaka-ui#827)", () => {
  const ask = (path: string, cookie?: string) =>
    proxy(
      new NextRequest(`https://www.orazaka.com${path}`, {
        headers: { host: "www.orazaka.com", ...(cookie ? { cookie } : {}) },
      }),
    );
  const hintedWith = () => (sessionHint as jest.Mock).mock.calls.at(-1)?.[2];

  it.each(["next-auth.session-token", "__Secure-next-auth.session-token"])(
    "hands %s to the hint when it exists",
    (name) => {
      ask("/pricing", `${name}=tok123`);
      expect(hintedWith()).toMatchObject({ cookie: name });
    },
  );
  it("falls back to the development name for a visitor with neither (the hint then clears itself)", () => {
    ask("/pricing");
    expect(hintedWith()).toMatchObject({ cookie: "next-auth.session-token" });
  });
  it("leaves the home for the page (it already decides on the server from the same cookie): no redirect", () => {
    expect(ask("/", "next-auth.session-token=tok123").headers.get("location")).toBeNull();
  });
});

describe("proxy with a language prefix (Loi 96)", () => {
  it.each(["/fr/login", "/en/login", "/fr", "/en/docs"])("serves %s as the page without its prefix", (path) => {
    const res = call(path);
    expect(res.status).toBe(200);
    expect(res.headers.get("x-middleware-rewrite")).toMatch(/orazaka\.com(\/login|\/|\/docs)$/);
  });

  it("gates a prefixed protected page like the page itself", () => {
    expect(call("/fr/jobs").headers.get("location")).toMatch(/\/login$/);
  });
});
