import { SiteFooter as FamilyFooter } from "@krizaka/ui/site-footer";
import { APP_VERSION } from "@/console/core/config/version";

/** The operator console's footer: @krizaka/ui's SiteFooter in its compact app mode (one line: « A Krizaka product » → www.krizaka.com, the version). */
export function SiteFooter() {
  return (
    <FamilyFooter current="orazaka" family={false} variant="app" version={APP_VERSION} data-testid="app-version" />
  );
}
