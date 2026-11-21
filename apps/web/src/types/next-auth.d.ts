import { DefaultSession } from "next-auth";

declare module "next-auth" {
  interface Session {
    /** Read by the page's JavaScript (`/api/auth/session`): never a backend token (krizaka-restricted#347). */
    user: {
      role?: string;
      activeInterceptions?: string[];
    } & DefaultSession["user"];
  }

  interface User {
    id: string;
    role?: string;
    activeInterceptions?: string[];
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    role?: string;
    activeInterceptions?: string[];
    /** The identity service's session JWT, replayed as the Bearer token by the BFF; server-side only (backend-token.ts). */
    backendToken?: string;
  }
}
