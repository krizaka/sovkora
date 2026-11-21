/** @jest-environment node */
// krizaka-restricted#347: every page answer carries a script-src with a nonce of its own, the same nonce Next.js reads
// from the request it renders (so its scripts carry it), on the public host and on the console's.
import { NextRequest } from "next/server";
import { proxy } from "@/proxy";

jest.mock("@krizaka/auth", () => ({ sessionHint: jest.fn((response: Response) => response) }), { virtual: true });

const NONCE = /'nonce-([A-Za-z0-9+/=]+)'/;

function ask(url: string, headers: Record<string, string> = {}) {
  const saved = { ...process.env };
  Object.assign(process.env, { AUTH_ALLOWED_IPS: "203.0.113.7", AUTH_CONSOLE_LOGIN_ENABLED: "true" });
  try {
    return proxy(new NextRequest(url, { headers: { host: new URL(url).host, ...headers } }));
  } finally {
    for (const k of Object.keys(process.env)) {
      if (!(k in saved)) {
        delete process.env[k];
      }
    }
    Object.assign(process.env, saved);
  }
}

/** The policy the browser gets, and the one (and the nonce) the page is rendered with. */
function policies(res: Response) {
  return {
    sent: res.headers.get("content-security-policy") ?? "",
    rendered: res.headers.get("x-middleware-request-content-security-policy") ?? "",
    nonce: res.headers.get("x-middleware-request-x-nonce") ?? "",
  };
}

describe("the Content-Security-Policy of a page", () => {
  it.each([
    ["a public page", "https://dev.sovkora.com/login", {}],
    ["a public page named by its language", "https://dev.sovkora.com/fr/pricing", {}],
    ["a console page", "https://admin.sovkora.com/login", { "do-connecting-ip": "203.0.113.7" }],
  ])("%s: a script runs only with this request's nonce, the one the page is rendered with", (_, url, headers) => {
    const { sent, rendered, nonce } = policies(ask(url, headers));
    expect(sent).toMatch(/script-src 'self' 'nonce-[^']+' 'strict-dynamic'/);
    expect(sent).toContain("frame-ancestors 'none'");
    expect(sent).toContain("base-uri 'self'");
    expect(sent).toContain("object-src 'none'");
    expect(sent).not.toContain("'unsafe-inline'");
    expect(rendered).toBe(sent);
    expect(sent.match(NONCE)?.[1]).toBe(nonce);
  });

  it("draws a new nonce for every request", () => {
    const first = policies(ask("https://dev.sovkora.com/login")).nonce;
    const second = policies(ask("https://dev.sovkora.com/login")).nonce;
    expect(first).not.toBe("");
    expect(first).not.toBe(second);
  });

  it("is on a redirect and a refusal too", () => {
    expect(ask("https://dev.sovkora.com/packs").headers.get("content-security-policy")).toMatch(NONCE);
    expect(ask("https://admin.sovkora.com/").headers.get("content-security-policy")).toMatch(NONCE);
  });
});
