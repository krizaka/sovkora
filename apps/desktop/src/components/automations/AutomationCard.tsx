import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { ConfirmButton } from "@krizaka/ui/confirm-button";
import { Switch } from "@krizaka/ui/switch";
import { automationsApi, baseName, slotLabel, type AutomationView } from "../../lib/api";
import { useI18n } from "../../lib/i18n";
import { RunStatusBadge } from "./RunStatusBadge";

const WEEKDAYS = [1, 2, 3, 4, 5];

export function AutomationCard({ automation: a, running, halted, act }: Readonly<{ automation: AutomationView; running: boolean; halted: boolean; act: (p: Promise<unknown>) => void }>) {
  const { t, format } = useI18n();
  const dateLocale = t("common.dateLocale");
  const weekdays = a.schedule.days.length === WEEKDAYS.length && WEEKDAYS.every((d) => a.schedule.days.includes(d));
  const schedule = format(t(weekdays ? "automations.card.scheduleWeekdays" : "automations.card.schedule"), { time: a.schedule.time });
  return (
    <Card.Root data-testid="automation">
      <Card.Body>
        <div className="flex flex-wrap items-center gap-2">
          <Card.Title as="h4">{a.name}</Card.Title>
          {running ? (
            <Badge tone="accent" dot pulse>{t("automations.card.running")}</Badge>
          ) : (
            <Badge tone={a.enabled ? "success" : "neutral"} dot>{t(a.enabled ? "automations.card.active" : "automations.card.paused")}</Badge>
          )}
          {a.dryRun && <Badge tone="warning">{t("automations.card.dryRun")}</Badge>}
        </div>
        <Card.Description>{schedule}</Card.Description>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-xs">
          <dt className="text-fg-secondary">{t("automations.card.folder")}</dt>
          <dd className="truncate font-mono" title={a.folder}>{baseName(a.folder)}</dd>
          <dt className="text-fg-secondary">{t("automations.card.model")}</dt>
          <dd className="font-mono">{a.model.model}</dd>
          <dt className="text-fg-secondary">{t("automations.card.next")}</dt>
          <dd>{slotLabel(a.nextRun, dateLocale) ?? t("automations.card.never")}</dd>
          <dt className="text-fg-secondary">{t("automations.card.last")}</dt>
          <dd>{a.lastRun ? <RunStatusBadge status={a.lastRun.status} /> : t("automations.card.noRun")}</dd>
        </dl>
        <Card.Footer className="flex-wrap">
          <Button size="sm" variant="primary" data-testid="automation-run" disabled={running || halted} onClick={() => act(automationsApi.runNow(a.id))}>
            {t("automations.card.runNow")}
          </Button>
          <Button size="sm" variant="secondary" onClick={() => act(automationsApi.setEnabled(a.id, !a.enabled))}>
            {t(a.enabled ? "automations.card.pause" : "automations.card.resume")}
          </Button>
          <span className="flex items-center gap-2">
            <Switch size="sm" label={t("automations.card.dryRunToggle")} checked={a.dryRun} onCheckedChange={(on) => act(automationsApi.setDryRun(a.id, on))} />
            <span aria-hidden>{t("automations.card.dryRunToggle")}</span>
          </span>
          <ConfirmButton size="sm" variant="ghost" className="ml-auto" confirmLabel={t("automations.card.deleteConfirm")} onConfirm={() => act(automationsApi.remove(a.id))}>
            {t("automations.card.delete")}
          </ConfirmButton>
        </Card.Footer>
      </Card.Body>
    </Card.Root>
  );
}
