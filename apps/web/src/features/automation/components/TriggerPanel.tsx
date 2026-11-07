"use client";

import * as React from "react";
import { formatDistanceToNow } from "date-fns";
import { enUS, fr } from "date-fns/locale";
import { TrashIcon } from "@krizaka/icons";
import { Badge } from "@krizaka/ui/badge";
import { Button, IconButton } from "@krizaka/ui/button";
import { Switch } from "@krizaka/ui/switch";
import { TriggerKindSchema, type Trigger } from "@krizaka/orazaka-shared";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { useTriggerMutations, useTriggers } from "@/features/automation/hooks/useAutomations";
import type { NewTrigger } from "@/services/automation.api";
import { ScheduleDialog } from "./ScheduleDialog";
import { SecretDialog, type RevealedWebhook } from "./SecretDialog";

function CopyAddress({ url }: Readonly<{ url: string }>) {
  const { t } = useTranslation();
  const [copied, setCopied] = React.useState(false);
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(url);
          setCopied(true);
        } catch {
          // The address is shown in full: it can be selected.
        }
      }}
    >
      {copied ? t.automations.triggers.copied : t.automations.triggers.copyUrl}
    </Button>
  );
}

/** One trigger: what it is, when it runs next, and what can be done with it. */
function TriggerRow({
  trigger,
  busy,
  onEnabled,
  onRotate,
  onRemove,
}: Readonly<{
  trigger: Trigger;
  busy: boolean;
  onEnabled: (enabled: boolean) => void;
  onRotate: () => void;
  onRemove: () => void;
}>) {
  const { t, locale } = useTranslation();
  const w = t.automations.triggers;
  const dateLocale = locale === "fr" ? fr : enUS;
  const when = (iso: string) => formatDistanceToNow(new Date(iso), { addSuffix: true, locale: dateLocale });
  const slot = trigger.schedule;
  const isWebhook = trigger.kind === TriggerKindSchema.enum.webhook;
  const days =
    slot && slot.days.length < 7
      ? slot.days.map((day) => t.automations.days[day as keyof typeof t.automations.days] ?? day).join(", ")
      : w.everyDay;
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border-subtle p-3" data-testid="trigger-row">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={isWebhook ? "accent" : "neutral"} size="sm">
          {isWebhook ? w.webhook : w.schedule}
        </Badge>
        <span className="min-w-0 flex-1 text-sm text-fg">
          {slot ? (
            fill(w.scheduleSummary, { days, time: slot.time, zone: slot.zone })
          ) : (
            <code className="break-all font-mono text-xs">{trigger.webhookUrl}</code>
          )}
        </span>
        <Switch
          label={trigger.enabled ? w.disable : w.enable}
          checked={trigger.enabled}
          disabled={busy}
          onCheckedChange={onEnabled}
        />
        {isWebhook && (
          <Button type="button" variant="ghost" size="sm" disabled={busy} onClick={onRotate}>
            {w.rotate}
          </Button>
        )}
        <IconButton variant="ghost" size="sm" label={w.remove} disabled={busy} onClick={onRemove}>
          <TrashIcon size={15} />
        </IconButton>
      </div>
      <p className="flex flex-wrap items-center gap-x-3 text-xs text-fg-muted">
        {trigger.webhookUrl && <CopyAddress url={trigger.webhookUrl} />}
        {trigger.secretHint && <span>{fill(w.secretHint, { hint: trigger.secretHint })}</span>}
        {trigger.enabled && trigger.nextFireAt && <span>{fill(w.nextRun, { when: when(trigger.nextFireAt) })}</span>}
        {trigger.lastFiredAt && <span>{fill(w.lastRun, { when: when(trigger.lastFiredAt) })}</span>}
      </p>
    </li>
  );
}

/**
 * The triggers of one automation: the signed webhooks and the schedules that start it. A new webhook's secret (and a
 * rotated one) is shown once in a dialog, then dropped — the list never has it.
 */
export function TriggerPanel({ automationId }: Readonly<{ automationId: string }>) {
  const { t } = useTranslation();
  const w = t.automations.triggers;
  const triggers = useTriggers(automationId);
  const mutations = useTriggerMutations(automationId);
  const [scheduling, setScheduling] = React.useState(false);
  const [revealed, setRevealed] = React.useState<RevealedWebhook | null>(null);
  const busy = Object.values(mutations).some((m) => m.isPending);

  const reveal = (trigger: Trigger) => {
    if (trigger.secret && trigger.webhookUrl) setRevealed({ url: trigger.webhookUrl, secret: trigger.secret });
  };
  const add = async (trigger: NewTrigger) => reveal(await mutations.add.mutateAsync(trigger));
  const failed = [mutations.add, mutations.rotate, mutations.remove, mutations.setEnabled].some((m) => m.isError);

  return (
    <div className="flex flex-col gap-3">
      {triggers.data?.length === 0 && <p className="text-sm text-fg-secondary">{w.empty}</p>}
      <ul className="flex flex-col gap-2">
        {(triggers.data ?? []).map((trigger) => (
          <TriggerRow
            key={trigger.id}
            trigger={trigger}
            busy={busy}
            onEnabled={(enabled) => mutations.setEnabled.mutate({ id: trigger.id, enabled })}
            onRotate={async () => {
              if (window.confirm(w.rotateConfirm)) reveal(await mutations.rotate.mutateAsync(trigger.id));
            }}
            onRemove={() => {
              if (window.confirm(w.removeConfirm)) mutations.remove.mutate(trigger.id);
            }}
          />
        ))}
      </ul>
      {failed && (
        <p role="alert" className="text-xs text-danger">
          {w.error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          size="sm"
          variant="secondary"
          disabled={busy}
          onClick={() => void add({ kind: "webhook" })}
        >
          {w.addWebhook}
        </Button>
        <Button type="button" size="sm" variant="secondary" disabled={busy} onClick={() => setScheduling(true)}>
          {w.addSchedule}
        </Button>
      </div>
      <ScheduleDialog open={scheduling} onClose={() => setScheduling(false)} onSubmit={add} />
      <SecretDialog revealed={revealed} onDone={() => setRevealed(null)} />
    </div>
  );
}
