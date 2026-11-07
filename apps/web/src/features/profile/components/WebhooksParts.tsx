"use client";

import * as React from "react";
import { formatDistanceToNow } from "date-fns";
import { enUS, fr } from "date-fns/locale";
import { z } from "zod";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { createKrizakaForm } from "@krizaka/ui/form";
import { useTranslation } from "@/core/context/LocaleContext";
import {
  EVENT_WORD,
  WEBHOOK_EVENTS,
  isHttpsUrl,
  type WebhookDelivery,
  type WebhookEvent,
} from "@/features/profile/hooks/useWebhooks";

/** The create form: one address, and the events to be told about. Defined once; its words are keys the section translates. */
const createWebhook = createKrizakaForm({
  schema: z.object({
    url: z.string().refine(isHttpsUrl, "webhooks.url.https"),
    completed: z.boolean(),
    failed: z.boolean(),
    cancelled: z.boolean(),
  }),
  defaultValues: { url: "", completed: true, failed: true, cancelled: false },
});

const EVENT_FIELDS = { "run.completed": "completed", "run.failed": "failed", "run.cancelled": "cancelled" } as const;

function eventsOf(values: { completed: boolean; failed: boolean; cancelled: boolean }): WebhookEvent[] {
  return WEBHOOK_EVENTS.filter((event) => values[EVENT_FIELDS[event]]);
}

/** The add-endpoint form, remounted at every opening so it starts clean. */
export function CreateWebhookForm({
  error,
  onSubmit,
}: Readonly<{
  error: "none" | "url" | "limit" | "generic";
  onSubmit: (url: string, events: WebhookEvent[]) => Promise<void>;
}>) {
  const { t } = useTranslation();
  const w = t.webhooks;
  const words: Record<string, string> = { "webhooks.url.https": w.invalidUrl };
  const form = createWebhook.useForm({
    i18n: { t: (key) => words[key] },
    onSubmit: async (values) => {
      await onSubmit(values.url.trim(), eventsOf(values));
    },
  });
  const messages: Record<string, string> = { limit: w.limitReached, url: w.invalidUrl, generic: w.createError };
  const message = (error && messages[error]) || null;
  return (
    <form.Form aria-label={w.create}>
      <form.TextField name="url" label={w.urlLabel} type="url" placeholder={w.urlPlaceholder} autoComplete="off" />
      <fieldset className="space-y-2">
        <legend className="text-xs font-medium text-fg-secondary">{w.eventsLabel}</legend>
        <form.Checkbox name="completed" label={w.events.completed} />
        <form.Checkbox name="failed" label={w.events.failed} />
        <form.Checkbox name="cancelled" label={w.events.cancelled} />
      </fieldset>
      {message && (
        <p role="alert" className="text-xs text-danger">
          {message}
        </p>
      )}
      <form.FormError />
      <form.Submit>{w.createCta}</form.Submit>
    </form.Form>
  );
}

/** The signing secret, shown once, with copy. */
export function RevealSecret({ secret, onDone }: Readonly<{ secret: string; onDone: () => void }>) {
  const { t } = useTranslation();
  const w = t.webhooks;
  const [copied, setCopied] = React.useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
    } catch {
      // The secret stays selectable in the field.
    }
  };
  return (
    <div className="space-y-4">
      <p className="text-xs text-fg-muted">{w.revealDesc}</p>
      <div className="flex items-center gap-2">
        <code
          data-testid="webhook-secret"
          className="min-w-0 flex-1 truncate rounded-md border border-border-subtle bg-surface-2 px-3 py-2 font-mono text-xs text-fg"
        >
          {secret}
        </code>
        <Button type="button" variant="secondary" size="sm" onClick={copy}>
          {copied ? w.copied : w.copy}
        </Button>
      </div>
      <div className="flex justify-end">
        <Button type="button" size="sm" onClick={onDone}>
          {w.done}
        </Button>
      </div>
    </div>
  );
}

const STATUS_TONE: Record<WebhookDelivery["status"], "neutral" | "success" | "danger"> = {
  PENDING: "neutral",
  DELIVERED: "success",
  DEAD: "danger",
};

/** The delivery log of one endpoint, newest first. */
export function DeliveryLog({ deliveries }: Readonly<{ deliveries: WebhookDelivery[] }>) {
  const { t, locale } = useTranslation();
  const w = t.webhooks;
  const dateLocale = locale === "fr" ? fr : enUS;
  if (deliveries.length === 0) return <p className="py-4 text-sm text-fg-secondary">{w.logEmpty}</p>;
  const columns: DataTableColumn<WebhookDelivery>[] = [
    {
      id: "event",
      header: w.logColumns.event,
      type: "title",
      value: (d) => w.events[EVENT_WORD[d.event]],
      cell: (d) => w.events[EVENT_WORD[d.event]],
    },
    {
      id: "status",
      header: w.logColumns.status,
      type: "status",
      value: (d) => w.status[d.status],
      cell: (d) => (
        <Badge tone={STATUS_TONE[d.status]} size="sm">
          {w.status[d.status]}
        </Badge>
      ),
    },
    {
      id: "attempts",
      header: w.logColumns.attempts,
      priority: 3,
      type: "number",
      align: "end",
      value: (d) => d.attempts,
    },
    {
      id: "answer",
      header: w.logColumns.answer,
      priority: 2,
      value: (d) => (d.lastHttpStatus ? String(d.lastHttpStatus) : (d.lastError ?? "")),
      cell: (d) => d.lastHttpStatus ?? d.lastError ?? "—",
    },
    {
      id: "when",
      header: w.logColumns.when,
      priority: 3,
      type: "date",
      value: (d) => new Date(d.createdAt),
      cell: (d) => formatDistanceToNow(new Date(d.createdAt), { addSuffix: true, locale: dateLocale }),
    },
  ];
  return (
    <DataTable
      caption={w.logTitle}
      rows={deliveries}
      columns={columns}
      getRowId={(d) => d.id}
      rowLabel={(d) => w.events[EVENT_WORD[d.event]]}
      labels={{ loading: w.loading, empty: w.logEmpty }}
    />
  );
}
