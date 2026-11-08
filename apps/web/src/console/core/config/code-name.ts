import { CODE_NAME } from "@/core/brand/brand";
import { fill } from "@/core/context/fill";
import { consoleCopy } from "@/console/core/copy/console";
import { APP_ENV } from "@/console/core/config/version";

/**
 * « code name: orazaka » under « Sovkora Operations » on a development console (`NEXT_PUBLIC_APP_ENV` is not `production`, the
 * condition of the DEV badge beside it); `null` on the production console. The environment is a parameter for the tests.
 */
export function consoleCodeName(env: string = APP_ENV): string | null {
  return env === "production" ? null : fill(consoleCopy.codeName, { code: CODE_NAME });
}
