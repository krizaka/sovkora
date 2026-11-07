/**
 * The console's own session cookie. Cookies are scoped to a host, not a port: under next-auth's default name the web
 * client (:3000) and this console (:3001) overwrite each other's session on localhost — signing in as a user on one
 * would sign the operator out of the other. Shared by `auth-options.ts` and the gate (`src/proxy.ts`), which must not
 * import next-auth's provider code.
 */
export const SESSION_COOKIE = "orazaka-admin.session-token";
