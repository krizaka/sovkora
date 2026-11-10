import { keepPreviousData, useQuery } from "@krizaka/query";
import { ActivityFeed } from "@krizaka/ui/activity-feed";
import { Badge } from "@krizaka/ui/badge";
import { automationsApi, type AuditView } from "../../lib/api";
import { useI18n } from "../../lib/i18n";

function auditTone(event: string): "warning" | "danger" | "neutral" {
  if (event.startsWith("approval") || event.startsWith("grant")) return "warning";
  if (event.startsWith("kill-switch")) return "danger";
  return "neutral";
}

/** The append-only, hash-chained audit log of this Mac, and whether its chain verifies. */
export function AuditLogView({ refreshKey }: { refreshKey: number }) {
  const { t, format } = useI18n();
  const auditQuery = useQuery({ queryKey: ["automations", "audit", refreshKey], queryFn: () => automationsApi.audit(200), placeholderData: keepPreviousData, retry: false });
  const audit: AuditView | null = auditQuery.data ?? null;

  const v = audit?.verification;
  return (
    <details className="group rounded-md border border-border-subtle p-3" data-testid="audit">
      <summary className="flex cursor-pointer items-center gap-2 text-sm font-semibold">
        {t("automations.audit.title")}
        {v && (
          <Badge tone={v.ok ? "success" : "danger"} dot>
            {v.ok ? format(t("automations.audit.verified"), { count: v.entries }) : format(t("automations.audit.broken"), { seq: v.brokenAt ?? 0 })}
          </Badge>
        )}
      </summary>
      <p className="mt-2 text-xs text-fg-secondary">{t("automations.audit.lead")}</p>
      <div className="mt-3">
        <ActivityFeed
          aria-label={t("automations.audit.title")}
          locale={t("common.dateLocale")}
          fold={false}
          events={(audit?.entries ?? []).map((e) => ({
            id: `${e.seq}`,
            at: e.at,
            title: `#${e.seq} ${e.event}`,
            description: `${JSON.stringify(e.data)} · ${e.hash.slice(0, 12)}`,
            tone: auditTone(e.event),
          }))}
          empty={t("automations.audit.none")}
        />
      </div>
    </details>
  );
}
