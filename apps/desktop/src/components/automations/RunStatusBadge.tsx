import { Badge } from "@krizaka/ui/badge";
import type { RunStatus } from "../../lib/api";
import { useI18n } from "../../lib/i18n";

const TONE = {
  running: "accent",
  "awaiting-approval": "warning",
  succeeded: "success",
  failed: "danger",
  cancelled: "neutral",
  halted: "danger",
  "skipped-budget": "warning",
} as const satisfies Record<RunStatus, "accent" | "warning" | "success" | "danger" | "neutral">;

export function RunStatusBadge({ status }: Readonly<{ status: RunStatus }>) {
  const { t } = useI18n();
  return (
    <Badge tone={TONE[status]} dot pulse={status === "running"}>
      {t(`automations.runs.status.${status}`)}
    </Badge>
  );
}
