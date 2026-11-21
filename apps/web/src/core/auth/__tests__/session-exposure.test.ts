/** @jest-environment node */
// The session object is what `/api/auth/session` hands to the page's JavaScript: the backend's session JWT (the
// Bearer every service accepts) must never be in it, on either host. The BFF reads it from the encrypted cookie.
import type { NextAuthOptions, Session, User } from "next-auth";
import type { JWT } from "next-auth/jwt";
import { authOptions as siteAuth } from "@/core/auth/auth-options";
import { authOptions as consoleAuth } from "@/console/core/auth/auth-options";

const BACKEND_JWT = "eyJhbGciOiJIUzI1NiJ9.backend-session.signature";

type JwtCallback = (params: Record<string, unknown>) => Promise<JWT>;
type SessionCallback = (params: Record<string, unknown>) => Promise<Session>;

/** The session the page would read after a credentials sign-in, through the options' own callbacks. */
async function sessionAfterSignIn(options: NextAuthOptions, user: User): Promise<Session> {
  const jwt = options.callbacks?.jwt as unknown as JwtCallback;
  const token = await jwt({
    token: { name: user.name, email: user.email },
    user,
    account: { provider: "credentials" },
  });
  const base: Session = { user: { name: user.name, email: user.email }, expires: "2099-01-01T00:00:00.000Z" };
  const session = options.callbacks?.session as unknown as SessionCallback | undefined;
  return session ? session({ session: base, token, user }) : base;
}

describe("the session handed to the page", () => {
  it("never carries the backend token on the public site", async () => {
    const session = await sessionAfterSignIn(siteAuth, {
      id: BACKEND_JWT,
      name: "eric",
      email: "eric@example.com",
      role: "user",
      activeInterceptions: ["PII"],
    });
    expect(JSON.stringify(session)).not.toContain(BACKEND_JWT);
    expect(session.user).toMatchObject({ email: "eric@example.com", role: "user", activeInterceptions: ["PII"] });
  });

  it("never carries the backend token on the console", async () => {
    const session = await sessionAfterSignIn(consoleAuth, { id: BACKEND_JWT, name: "eric", email: "eric@example.com" });
    expect(JSON.stringify(session)).not.toContain(BACKEND_JWT);
    expect(session.user).toMatchObject({ email: "eric@example.com" });
  });

  it("keeps the backend token in the server-side JWT, where the BFF reads it", async () => {
    const jwt = siteAuth.callbacks?.jwt as unknown as JwtCallback;
    const token = await jwt({ token: {}, user: { id: BACKEND_JWT }, account: { provider: "credentials" } });
    expect(JSON.stringify(token)).toContain(BACKEND_JWT);
  });
});
