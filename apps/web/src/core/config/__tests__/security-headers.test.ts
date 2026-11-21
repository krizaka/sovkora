import { contentSecurityPolicy, newNonce, SECURITY_HEADERS } from "@/core/config/security-headers";

describe("security headers", () => {
  const h = Object.fromEntries(SECURITY_HEADERS.map((x) => [x.key, x.value]));
  it("forbid framing, sniffing and plain HTTP", () => {
    expect(h["Strict-Transport-Security"]).toMatch(/max-age=\d{8}/);
    expect(h["X-Content-Type-Options"]).toBe("nosniff");
    expect(h["X-Frame-Options"]).toBe("DENY");
    expect(h["Content-Security-Policy"]).toContain("frame-ancestors 'none'");
    expect(h["Referrer-Policy"]).toBe("strict-origin-when-cross-origin");
  });
});

describe("contentSecurityPolicy", () => {
  it("keeps the base policy and lets a script run only by its nonce", () => {
    expect(contentSecurityPolicy("abc", false)).toBe(
      "frame-ancestors 'none'; base-uri 'self'; object-src 'none'; script-src 'self' 'nonce-abc' 'strict-dynamic'",
    );
  });

  it("allows eval only in development", () => {
    expect(contentSecurityPolicy("abc", true)).toMatch(/'strict-dynamic' 'unsafe-eval'$/);
    expect(contentSecurityPolicy("abc", false)).not.toContain("unsafe-eval");
  });

  it("draws 128 random bits per nonce", () => {
    expect(atob(newNonce())).toHaveLength(16);
    expect(newNonce()).not.toBe(newNonce());
  });
});
