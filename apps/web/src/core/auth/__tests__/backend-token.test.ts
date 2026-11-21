/** @jest-environment node */
// The BFF reads the backend's session JWT from NextAuth's encrypted cookie — not from the session the page reads.
import { NextRequest } from "next/server";
import { encode } from "next-auth/jwt";
import { backendToken } from "@/core/auth/backend-token";
import { SESSION_COOKIE } from "@/console/core/auth/session-cookie";

jest.mock("server-only", () => ({}));

const SEAL = "a-test-sealing-value-long-enough-for-a-session";
const BACKEND_JWT = "eyJhbGciOiJIUzI1NiJ9.backend-session.signature";

const withCookie = (cookie?: string) =>
  new NextRequest("http://localhost:3000/api/v1/profile", { headers: cookie ? { cookie } : {} });
const sealed = (seal: string) => encode({ token: { backendToken: BACKEND_JWT }, secret: seal });

describe("backendToken", () => {
  it("reads the backend token from the site's session cookie", async () => {
    const cookie = await sealed(SEAL);
    expect(await backendToken(withCookie(`next-auth.session-token=${cookie}`), { secret: SEAL })).toBe(BACKEND_JWT);
  });

  it("reads the console's own cookie, sealed by the console's own value", async () => {
    const req = withCookie(`${SESSION_COOKIE}=${await sealed(SEAL)}`);
    expect(await backendToken(req, { cookieName: SESSION_COOKIE, secret: SEAL })).toBe(BACKEND_JWT);
  });

  it("is null without a session, or with one sealed by another value", async () => {
    expect(await backendToken(withCookie(), { secret: SEAL })).toBeNull();
    const forged = await sealed("another-sealing-value-another-value");
    expect(await backendToken(withCookie(`next-auth.session-token=${forged}`), { secret: SEAL })).toBeNull();
  });
});
