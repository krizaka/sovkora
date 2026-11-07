import { ApiError } from "../config/api";

/** What went wrong, in the terms a person can act on — never the server's own (English) wording. */
export type ErrorKind = "unauthorized" | "server" | "unreachable" | "other";

/**
 * 401 → the session ended; 5xx → the platform is struggling; a network failure → the phone cannot reach it;
 * anything else is the screen's own failure ("the Studio could not be installed").
 */
export function errorKind(cause: unknown): ErrorKind {
  if (cause instanceof ApiError) {
    if (cause.status === 401) return "unauthorized";
    if (cause.status >= 500) return "server";
    return "other";
  }
  if (cause instanceof TypeError) return "unreachable";
  return "other";
}
