/** @jest-environment node */
// Contract with krizaka-users (orazaka-web-client / krizaka-ts#234): `OAuthRequest(provider, idToken, email, username)`
// refuses a blank provider or idToken, so sign-up with Google or GitHub failed on every call while we sent only email + username.
import type { Account } from "next-auth";
import { buildOAuthExchangeBody, OAUTH_PROVIDERS } from "@/core/auth/oauth-exchange";

/** Mirror of `OAuthRequest`'s compact constructor in krizaka-users. */
const REQUIRED_BY_USERS = ["provider", "idToken"] as const;
const accepted = (body: object | null) =>
  body !== null &&
  REQUIRED_BY_USERS.every((k) => {
    const v = (body as Record<string, unknown>)[k];
    return typeof v === "string" && v.trim() !== "";
  });

const account = (a: Partial<Account> & { provider: string }): Account =>
  ({ type: "oauth", providerAccountId: "1", ...a }) as Account;
const user = { email: "eric@example.com", name: "Eric" };

describe("OAuth token exchange body", () => {
  it("sends Google's id token with the provider", () => {
    const body = buildOAuthExchangeBody(
      account({ provider: "google", id_token: "g.id.token", access_token: "g-access" }),
      user,
    );
    expect(body).toEqual({ provider: "google", idToken: "g.id.token", email: "eric@example.com", username: "Eric" });
    expect(accepted(body)).toBe(true);
  });

  it("sends GitHub's access token (GitHub has no id token)", () => {
    const body = buildOAuthExchangeBody(account({ provider: "github", access_token: "gho_x" }), {
      email: "e@example.com",
      name: null,
    });
    expect(body).toMatchObject({ provider: "github", idToken: "gho_x", username: "e" });
    expect(accepted(body)).toBe(true);
  });

  it("makes no call when the credential is missing or blank", () => {
    expect(buildOAuthExchangeBody(account({ provider: "google", access_token: "only-access" }), user)).toBeNull();
    expect(buildOAuthExchangeBody(account({ provider: "github", access_token: "  " }), user)).toBeNull();
  });

  it("ignores accounts that are not OAuth providers of this site", () => {
    expect(buildOAuthExchangeBody(account({ provider: "credentials" }), user)).toBeNull();
    expect(buildOAuthExchangeBody(null, user)).toBeNull();
  });

  it("covers every offered provider", () => {
    for (const provider of OAUTH_PROVIDERS) {
      expect(accepted(buildOAuthExchangeBody(account({ provider, id_token: "t", access_token: "t" }), user))).toBe(
        true,
      );
    }
  });
});
