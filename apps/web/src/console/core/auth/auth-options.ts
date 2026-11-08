/**
 * @file auth-options.ts
 * @description NextAuth configuration for the SecOps console.
 *
 * Credentials only, unlike the user client: an admin console is not a place to
 * accept a social identity provider, because the role that matters here is
 * granted by the identity service and an OAuth account proves nothing about it.
 *
 * The session JWT the identity service issues is carried verbatim and replayed
 * as the Bearer token by the BFF proxy. Every billing write is
 * `@PreAuthorize("hasRole('ADMIN')")` server-side, so this token — not anything
 * this app decides — is what actually authorises an action.
 */

import type { NextAuthOptions } from "next-auth";
import CredentialsProvider from "next-auth/providers/credentials";
import { SESSION_COOKIE } from "@/console/core/auth/session-cookie";
import { consoleAuthKey } from "@/console/core/auth/session-key";

const ADMIN_AUTHORITY = "ROLE_ADMIN";

export const authOptions: NextAuthOptions = {
  // The console signs its sessions with its own key, never the public site's (no shared session between the hosts).
  secret: consoleAuthKey(process.env),
  providers: [
    CredentialsProvider({
      name: "Sovkora",
      credentials: {
        email: { label: "Email", type: "email" },
        password: { label: "Password", type: "password" },
      },
      async authorize(credentials) {
        if (!credentials?.email || !credentials?.password) {
          return null;
        }
        const edgeUrl = process.env.ROUTER_URL || "http://localhost:8088";
        try {
          const res = await fetch(`${edgeUrl}/api/v1/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              email: credentials.email,
              password: credentials.password,
            }),
          });
          if (!res.ok) {
            return null;
          }
          const data = await res.json();
          if (!data?.token) {
            return null;
          }
          // Refused here as well as server-side. The backend is the real gate, but
          // letting a non-admin hold a console session would show them screens
          // whose every action then 403s — a worse experience than a clean refusal.
          if (!data.authorities?.includes(ADMIN_AUTHORITY)) {
            return null;
          }
          return {
            id: data.token,
            name: data.username,
            email: data.email || credentials.email,
          };
        } catch {
          return null;
        }
      },
    }),
  ],
  session: { strategy: "jwt" },
  cookies: {
    sessionToken: {
      name: SESSION_COOKIE,
      options: { httpOnly: true, sameSite: "lax", path: "/", secure: process.env.NODE_ENV === "production" },
    },
  },
  pages: { signIn: "/login" },
  callbacks: {
    async jwt({ token, user }) {
      if (user) {
        token.sessionToken = user.id;
      }
      return token;
    },
    async session({ session, token }) {
      if (session.user) {
        session.user.token = token.sessionToken as string;
      }
      return session;
    },
  },
};
