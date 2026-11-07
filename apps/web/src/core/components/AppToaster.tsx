"use client";

import { Toaster } from "@krizaka/ui/toast";
import { useTranslation } from "@/core/context/LocaleContext";

/**
 * The platform Toaster (sonner, styled with the roles), mounted once in `Providers`: every `toast()` /
 * `toast.success|error|info|warning()` of the app lands here, with its region and close button named in the
 * current language.
 */
export function AppToaster() {
  const { t } = useTranslation();
  return <Toaster label={t.notifications.region} closeLabel={t.notifications.dismiss} />;
}
