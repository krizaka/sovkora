"use client";

import { EnvironmentNotice } from "@krizaka/ui/environment-notice";

import { useTranslation } from "@/core/context/LocaleContext";

/**
 * « Development version, data can be reset, not a commercial offer yet » (krizaka-internal#211): the `EnvironmentNotice` of
 * @krizaka/ui — the same piece and words as every Krizaka site — at the top of every public page of a deployment that is not
 * open to the public (APP_SEARCH_INDEXING=off: dev, preview). The server decides (`show`), the visitor's language words it.
 * Never a banner of our own.
 */
export function StageNotice({ show }: Readonly<{ show: boolean }>) {
  const { t } = useTranslation();
  return (
    <EnvironmentNotice stage={show ? "dev" : "prod"} title={t.landing.stage.title} message={t.landing.stage.message} />
  );
}
