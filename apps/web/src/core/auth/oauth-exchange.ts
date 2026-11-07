/**
 * @file oauth-exchange.ts
 * @description The Token-Exchange call NextAuth makes to the gateway after an OAuth sign-in (`POST /api/v1/auth/oauth`).
 *
 * krizaka-users' `OAuthRequest` requires `provider` and `idToken` (the identity is read from the verified token, never
 * from the hints). Each provider hands NextAuth a different credential — Google an OIDC id token, GitHub only an access
 * token — so the choice is a typed map per provider (ADR-015), not an `if` ladder: a new provider that is not in the map
 * does not compile, and one that is missing a credential at runtime is refused before any call is made.
 */
import type { Account, User } from "next-auth";

/** The OAuth providers this site offers; the wire value of `provider` is the id itself (krizaka-users matches it case-insensitively). */
export const OAUTH_PROVIDERS = ["github", "google"] as const;
export type OAuthProvider = (typeof OAUTH_PROVIDERS)[number];

/** The credential each provider's verifier on krizaka-users expects as `idToken`. */
const CREDENTIAL: Record<OAuthProvider, (account: Account) => string | undefined> = {
  google: (account) => account.id_token,
  github: (account) => account.access_token,
};

/** The body of `POST /api/v1/auth/oauth`, the shape of krizaka-users' `OAuthRequest`. */
export interface OAuthExchangeBody {
  provider: OAuthProvider;
  idToken: string;
  email?: string;
  username?: string;
}

export const isOAuthProvider = (id: string): id is OAuthProvider => (OAUTH_PROVIDERS as readonly string[]).includes(id);

/** The body to send, or `null` when the account is not an OAuth one we serve or carries no usable credential. */
export function buildOAuthExchangeBody(
  account: Account | null | undefined,
  user: Pick<User, "email" | "name">,
): OAuthExchangeBody | null {
  if (!account || !isOAuthProvider(account.provider)) return null;
  const idToken = CREDENTIAL[account.provider](account)?.trim();
  if (!idToken) return null;
  const email = user.email ?? undefined;
  return { provider: account.provider, idToken, email, username: user.name || email?.split("@")[0] };
}
