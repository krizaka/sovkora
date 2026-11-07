import type { ErrorKind } from "../core/errorKind";
import { errorKind } from "../core/errorKind";
import type { Key, Messages } from "./index";
import type { Translate } from "@krizaka/i18n";

/**
 * The words for a failure, in the person's language. `fallback` is the screen's own sentence for "other" failures
 * (a 4xx the platform refused with): the server's English problem detail is never shown.
 */
export function errorText(cause: unknown, t: Translate<Messages>, fallback: Key): string {
  const kind: ErrorKind = errorKind(cause);
  if (kind === "unauthorized") return t("common.sessionExpired");
  if (kind === "server") return t("common.unavailable");
  if (kind === "unreachable") return t("common.unreachable");
  return t(fallback);
}
