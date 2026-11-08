/** @jest-environment node */
// The console's guard (orazaka-web-client#275): only an account with the administrator role holds a console session,
// so no screen of it — Law → Proposals included — is reachable by a member. The services refuse them (403) as well.
import { authOptions } from "@/console/core/auth/auth-options";

type Authorize = (credentials: Record<string, string> | undefined, request: unknown) => Promise<unknown>;
const authorize = (authOptions.providers[0] as unknown as { options: { authorize: Authorize } }).options.authorize;

const signIn = (authorities: string[]) => {
  global.fetch = jest.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ token: "jwt", username: "eric", email: "eric@example.com", authorities }),
  }) as never;
  return authorize({ email: "eric@example.com", password: "a-password" }, {});
};

describe("console sign-in", () => {
  it("refuses an account without the administrator role", async () => {
    expect(await signIn(["ROLE_USER"])).toBeNull();
  });

  it("opens a session for an administrator", async () => {
    expect(await signIn(["ROLE_USER", "ROLE_ADMIN"])).toEqual({ id: "jwt", name: "eric", email: "eric@example.com" });
  });
});
