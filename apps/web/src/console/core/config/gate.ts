/**
 * @file gate.ts
 * @description The decision of the proxy, pure: let the request through, answer 404 (closed site, BFF route) or send
 * it to /login (a page without an operator session).
 */
import { authClosed, isBackendPath, type AuthFlags } from "./auth-flags.ts";

export type GateDecision = "pass" | "not-found" | "login";

export function gate(pathname: string, hasSession: boolean, flags: AuthFlags): GateDecision {
  if (authClosed(flags) && isBackendPath(pathname)) return "not-found";
  if (pathname === "/login" || pathname.startsWith("/api/")) return "pass";
  return hasSession ? "pass" : "login";
}
