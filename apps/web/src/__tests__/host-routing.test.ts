/** @jest-environment node */
/**
 * The admin console is served by this app on its own host (admin.orazaka.com, formerly the standalone orazaka-web-admin):
 * the proxy rewrites that host to the internal `/console` tree, and the public host never reaches it.
 */
import { NextRequest } from "next/server";
import { proxy } from "@/proxy";
import { isAdminHost, routeFor } from "@/console/core/config/host";

const ALLOWED = "203.0.113.7";
const OTHER = "198.51.100.1";

function call(host: string, path: string, opts: { ip?: string; cookie?: string; env?: Record<string, string> } = {}) {
  const saved = { ...process.env };
  Object.assign(process.env, { AUTH_ALLOWED_IPS: ALLOWED, AUTH_CONSOLE_LOGIN_ENABLED: "true" }, opts.env ?? {});
  try {
    const headers: Record<string, string> = { host };
    if (opts.ip) headers["do-connecting-ip"] = opts.ip;
    if (opts.cookie) headers.cookie = opts.cookie;
    return proxy(new NextRequest(`https://${host}${path}`, { headers }));
  } finally {
    for (const k of Object.keys(process.env)) if (!(k in saved)) delete process.env[k];
    Object.assign(process.env, saved);
  }
}
const rewritten = (res: Response) => res.headers.get("x-middleware-rewrite");

describe("routeFor (pure)", () => {
  it("recognises the admin host only by its first label", () => {
    expect(isAdminHost("admin.orazaka.com")).toBe(true);
    expect(isAdminHost("ADMIN.orazaka.com:443")).toBe(true);
    expect(isAdminHost("dev.orazaka.com")).toBe(false);
    expect(isAdminHost("administrator.example.com")).toBe(false);
    expect(isAdminHost(null)).toBe(false);
  });
  it("rewrites every admin-host path under /console, never twice", () => {
    expect(routeFor("admin.orazaka.com", "/")).toEqual({ kind: "rewrite", path: "/console" });
    expect(routeFor("admin.orazaka.com", "/studios")).toEqual({ kind: "rewrite", path: "/console/studios" });
    expect(routeFor("admin.orazaka.com", "/console/studios")).toEqual({ kind: "notFound" });
  });
  it("answers 404 for /console on any other host", () => {
    for (const p of [
      "/console",
      "/console/",
      "/console/users",
      "/console/api/v1/billing/plans",
      "/console/api/auth/session",
    ]) {
      expect(routeFor("dev.orazaka.com", p)).toEqual({ kind: "notFound" });
    }
    expect(routeFor("dev.orazaka.com", "/consoles")).toEqual({ kind: "next" });
  });
});

describe("public host", () => {
  it.each([
    "/console",
    "/console/users",
    "/console/login",
    "/console/api/v1/billing/plans",
    "/console/api/auth/session",
    "/console/robots.txt",
  ])("never serves the admin tree: 404 on %s", (path) => {
    expect(call("dev.orazaka.com", path, { ip: ALLOWED }).status).toBe(404);
  });
  it.each(["/", "/docs", "/login", "/desktop", "/api/healthz"])("is unaffected: %s passes", (path) => {
    const res = call("dev.orazaka.com", path);
    expect(res.status).toBe(200);
    expect(res.headers.get("x-middleware-next")).toBe("1");
    expect(rewritten(res)).toBeNull();
  });
  it("is not gated by the admin allow-list", () => {
    expect(call("dev.orazaka.com", "/", { ip: OTHER }).status).toBe(200);
  });
});

describe("admin host", () => {
  it("refuses a client address that is not allowed, sign-in included (403)", () => {
    for (const path of ["/", "/login", "/users", "/api/v1/billing/plans", "/api/auth/session"]) {
      expect(call("admin.orazaka.com", path, { ip: OTHER }).status).toBe(403);
    }
  });
  it("refuses everybody in production when the allow-list is empty, and when the edge sends no address", () => {
    expect(
      call("admin.orazaka.com", "/login", { ip: ALLOWED, env: { AUTH_ALLOWED_IPS: "", NODE_ENV: "production" } })
        .status,
    ).toBe(403);
    expect(call("admin.orazaka.com", "/login", { env: { NODE_ENV: "production" } }).status).toBe(403);
  });
  it("lets an allowed address reach the console: the login page is rewritten under /console", () => {
    const res = call("admin.orazaka.com", "/login", { ip: ALLOWED });
    expect(res.status).toBe(200);
    expect(rewritten(res)).toContain("/console/login");
    expect(res.headers.get("x-robots-tag")).toMatch(/noindex/);
  });
  it("sends a visitor without the console's session to /login, on the admin host", () => {
    const res = call("admin.orazaka.com", "/studios", { ip: ALLOWED });
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toBe("https://admin.orazaka.com/login?callbackUrl=%2Fstudios");
  });
  it("accepts only the console's own session cookie, not the public host's", () => {
    expect(call("admin.orazaka.com", "/studios", { ip: ALLOWED, cookie: "next-auth.session-token=x" }).status).toBe(
      307,
    );
    const res = call("admin.orazaka.com", "/studios", { ip: ALLOWED, cookie: "orazaka-admin.session-token=x" });
    expect(rewritten(res)).toContain("/console/studios");
  });
  it("rewrites the BFF routes under /console too, and answers 404 while the console sign-in is closed", () => {
    expect(rewritten(call("admin.orazaka.com", "/api/v1/billing/plans", { ip: ALLOWED }))).toContain(
      "/console/api/v1/billing/plans",
    );
    expect(
      call("admin.orazaka.com", "/api/v1/billing/plans", { ip: ALLOWED, env: { AUTH_CONSOLE_LOGIN_ENABLED: "false" } })
        .status,
    ).toBe(404);
  });
  it("is not opened or closed by the public site's flag (separate flag)", () => {
    expect(
      call("admin.orazaka.com", "/api/auth/session", { ip: ALLOWED, env: { AUTH_LOGIN_ENABLED: "false" } }).status,
    ).toBe(200);
  });
  it("answers 404 for an explicit /console path; the public SEO files are looked for under /console, where they do not exist", () => {
    expect(call("admin.orazaka.com", "/console/users", { ip: ALLOWED }).status).toBe(404);
    const res = call("admin.orazaka.com", "/sitemap.xml", { ip: ALLOWED, cookie: "orazaka-admin.session-token=x" });
    expect(rewritten(res)).toContain("/console/sitemap.xml");
  });
  it("keeps /api/healthz for the platform probe, whatever the address", () => {
    const res = call("admin.orazaka.com", "/api/healthz", { ip: OTHER });
    expect(res.status).toBe(200);
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });
});

describe("robots.txt on the admin host (dev-preview admin.noindex)", () => {
  it("is answered for any address, before the gate: Disallow everything, and noindex", async () => {
    const res = call("admin.orazaka.com", "/robots.txt", { ip: OTHER });
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toMatch(/text\/plain/);
    expect(await res.text()).toMatch(/User-agent: \*\s+Disallow: \/\s*$/);
    expect(res.headers.get("x-robots-tag")).toMatch(/noindex/);
  });
  it("leaves the public host's robots.txt to the site (indexed)", () => {
    expect(rewritten(call("dev.orazaka.com", "/robots.txt"))).toBeNull();
  });
});
