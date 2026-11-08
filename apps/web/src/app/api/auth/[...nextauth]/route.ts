/**
 * @file route.ts
 * @description NextAuth API route handler. Configuration is defined in
 * {@link @/core/auth/auth-options} and imported here to comply with the
 * `no-auth-config-in-routing` dependency-cruiser rule.
 */

import NextAuth from "next-auth";
import { authOptions } from "@/core/auth/auth-options";

const handler = NextAuth(authOptions);

export { handler as GET, handler as POST };
