/**
 * @file auth-options.ts
 * @description NextAuth configuration extracted from the routing tree per dependency-cruiser rule
 * `no-auth-config-in-routing`. Defines providers (OAuth + Credentials), JWT/Session callbacks,
 * and custom page routes for the Next.js BFF authentication layer.
 *
 * All direct user credentials authentication is delegated to the gateway and validated on the backend.
 *
 * @see {@link http://localhost:8080/api/v1/auth/login} - Upstream credentials auth endpoint.
 */

import type { Account, NextAuthOptions, User } from "next-auth";
import type { JWT } from "next-auth/jwt";
import GithubProvider from "next-auth/providers/github";
import GoogleProvider from "next-auth/providers/google";
import CredentialsProvider from "next-auth/providers/credentials";
import { buildOAuthExchangeBody, isOAuthProvider } from "@/core/auth/oauth-exchange";
import { githubConfigured, googleConfigured, providerOffered, parseFlag } from "@/core/config/auth-flags";

const signup = parseFlag(process.env.AUTH_SIGNUP_ENABLED);
/** OAuth providers exist only when configured and when this site accepts new accounts (the gateway provisions on first sign-in). */
const oauthProviders = [
  ...(providerOffered(githubConfigured(process.env), signup)
    ? [GithubProvider({ clientId: process.env.GITHUB_ID as string, clientSecret: process.env.GITHUB_SECRET as string })]
    : []),
  ...(providerOffered(googleConfigured(process.env), signup)
    ? [
        GoogleProvider({
          clientId: process.env.GOOGLE_CLIENT_ID as string,
          clientSecret: process.env.GOOGLE_CLIENT_SECRET as string,
        }),
      ]
    : []),
];

/** Exchanges the provider's credential for a gateway session (JIT provisioning) and carries it in the JWT. */
async function provisionOAuthUser(jwt: JWT, account: Account, user: User): Promise<void> {
  const exchange = buildOAuthExchangeBody(account, user);
  if (!exchange) {
    console.error("OAuth sign-in without a usable credential for provider:", account.provider);
    return;
  }
  try {
    const gatewayUrl = process.env.ROUTER_URL || process.env.GATEWAY_URL || "http://localhost:8080";
    const response = await fetch(`${gatewayUrl}/api/v1/auth/oauth`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(exchange),
    });
    if (!response.ok) {
      console.error("Failed to JIT provision OAuth user:", response.statusText);
      return;
    }
    const dbUser = await response.json();
    jwt.id = dbUser.token;
    jwt.role = "user";
    jwt.activeInterceptions = dbUser.active_interceptions || [];
  } catch (error) {
    console.error("Error during JIT provisioning:", error);
  }
}

/**
 * NextAuth options defining authentication providers, custom callbacks, pages routing,
 * and JWT session mapping for the Next.js application.
 */
export const authOptions: NextAuthOptions = {
  providers: [
    ...oauthProviders,
    CredentialsProvider({
      name: "Gateway",
      credentials: {
        email: {
          label: "Email",
          type: "email",
          placeholder: "user@orazaka.com",
        },
        password: { label: "Password", type: "password" },
      },
      async authorize(credentials) {
        if (!credentials?.email || !credentials?.password) {
          return null;
        }

        const gatewayUrl = process.env.ROUTER_URL || process.env.GATEWAY_URL || "http://localhost:8080";

        try {
          const res = await fetch(`${gatewayUrl}/api/v1/auth/login`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              email: credentials.email,
              password: credentials.password,
            }),
          });

          if (!res.ok) {
            return null;
          }

          // returns { token: "<session JWT>", username, email, authorities: [...], active_interceptions: [...] }
          // `token` is the signed HS256 session JWT every backend validates — it is carried
          // through as `user.id` and replayed verbatim as the Bearer token by the BFF proxies.
          const data = await res.json();
          if (data && data.token) {
            const role = data.authorities?.includes("ROLE_ADMIN") ? "admin" : "user";
            return {
              id: data.token,
              name: data.username,
              email: data.email || credentials.email,
              role: role,
              activeInterceptions: data.active_interceptions || [],
            };
          }
        } catch (error) {
          console.error("NextAuth authorize error calling gateway login:", error);
        }
        return null;
      },
    }),
  ],
  pages: {
    signIn: "/login",
  },
  callbacks: {
    async jwt({ token, user, account, trigger, session }) {
      if (user) {
        if (account && isOAuthProvider(account.provider)) {
          await provisionOAuthUser(token, account, user);
        } else {
          token.role = user.role;
          token.id = user.id;
          token.activeInterceptions = user.activeInterceptions || [];
        }
      }
      // If we trigger an update of the session on the client side, sync activeInterceptions
      if (trigger === "update" && session?.activeInterceptions) {
        token.activeInterceptions = session.activeInterceptions;
      }
      return token;
    },
    async session({ session, token }) {
      if (session.user) {
        session.user.role = token.role;
        session.user.id = token.id;
        session.user.activeInterceptions = token.activeInterceptions || [];
      }
      return session;
    },
  },
  session: {
    strategy: "jwt",
  },
};
