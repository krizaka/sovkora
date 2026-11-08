import { DefaultSession } from "next-auth";

declare module "next-auth" {
  interface Session {
    user: {
      id?: string;
      role?: string;
      activeInterceptions?: string[];
      /** Console only: the identity service's session JWT, replayed as the Bearer token by the console's BFF. */
      token?: string;
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
    id?: string;
    role?: string;
    activeInterceptions?: string[];
    /** Console only: the identity service's session JWT. */
    sessionToken?: string;
  }
}
