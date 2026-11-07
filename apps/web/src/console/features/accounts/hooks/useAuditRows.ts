"use client";

import * as React from "react";
import { PlatformAdminApi } from "@/console/services/platform.admin.api";
import { mergeAudit, type AuditRow } from "@/console/features/accounts/utils/audit";

/** A call that reports its outcome instead of throwing: one service down must not hide the others' lines. */
const attempt = <T>(p: Promise<T>) =>
  p.then(
    (value) => ({ ok: true as const, value }),
    (error: unknown) => ({ ok: false as const, error }),
  );

/** Every service's operator audit in one list (ADR-085); a service that does not answer leaves the others, and `failed` says so. */
export function useAuditRows() {
  const [rows, setRows] = React.useState<AuditRow[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [failed, setFailed] = React.useState(false);
  const load = React.useCallback(async () => {
    setLoading(true);
    const [identity, billing, studio, engine] = await Promise.all([
      attempt(PlatformAdminApi.audit()),
      attempt(PlatformAdminApi.serviceAudit("billing")),
      attempt(PlatformAdminApi.serviceAudit("studio")),
      attempt(PlatformAdminApi.serviceAudit("engine")),
    ]);
    const all = [identity, billing, studio, engine];
    setLoading(false);
    setFailed(all.some((r) => !r.ok));
    if (all.every((r) => !r.ok)) return;
    const lines = (r: typeof billing) => (r.ok ? r.value : null);
    setRows(
      mergeAudit(identity.ok ? identity.value : null, {
        billing: lines(billing),
        studio: lines(studio),
        engine: lines(engine),
      }),
    );
  }, []);
  React.useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);
  return { rows, loading, failed, reload: load } as const;
}
