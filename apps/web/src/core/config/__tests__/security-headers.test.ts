import { SECURITY_HEADERS } from "@/core/config/security-headers";

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
