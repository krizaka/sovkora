"use client";

import * as React from "react";
import { enUS, fr } from "date-fns/locale";

import { timeAgoCell } from "@/core/components/timeAgo";
import { EyeIcon, TrashIcon } from "@krizaka/icons";
import { Icon } from "@krizaka/orazaka-design-system";
import { Badge } from "@krizaka/ui/badge";
import { Button, IconButton } from "@krizaka/ui/button";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { Dialog } from "@krizaka/ui/dialog";
import { SettingsSection } from "@krizaka/ui/settings-layout";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { EVENT_WORD, useWebhooks, type WebhookEndpoint } from "@/features/profile/hooks/useWebhooks";
import { CreateWebhookForm, DeliveryLog, RevealSecret } from "./WebhooksParts";

interface WebhooksSectionProps {
  fetchHeaders: () => Promise<Record<string, string>>;
}

const urlCell = (e: WebhookEndpoint) => <code className="break-all font-mono text-xs">{e.url}</code>;

/**
 * Webhooks settings: the endpoints that are told when a run is over, on the kit's DataTable (cards on a phone), with a
 * form to add one. The signing secret is shown once in a dialog (create, rotate); each endpoint has its delivery log.
 */
export function WebhooksSection({ fetchHeaders }: Readonly<WebhooksSectionProps>) {
  const { t, locale } = useTranslation();
  const w = t.webhooks;
  const dateLocale = locale === "fr" ? fr : enUS;
  const hook = useWebhooks(fetchHeaders);
  const [isCreateOpen, setCreateOpen] = React.useState(false);

  const columns = React.useMemo<DataTableColumn<WebhookEndpoint>[]>(
    () => [
      {
        id: "url",
        header: w.columns.url,
        type: "title",
        filter: "text",
        sortable: true,
        value: (e) => e.url,
        cell: urlCell,
      },
      {
        id: "events",
        header: w.columns.events,
        priority: 2,
        value: (e) => e.events.map((event) => w.events[EVENT_WORD[event]]).join(", "),
        cell: (e) => (
          <span className="inline-flex flex-wrap gap-1">
            {e.events.map((event) => (
              <Badge key={event} tone="neutral" size="sm">
                {w.events[EVENT_WORD[event]]}
              </Badge>
            ))}
          </span>
        ),
      },
      {
        id: "created",
        header: w.columns.created,
        priority: 3,
        type: "date",
        sortable: true,
        value: (e) => new Date(e.createdAt),
        cell: (e) => timeAgoCell(e.createdAt, dateLocale),
      },
      {
        id: "actions",
        header: w.columns.actions,
        headerHidden: true,
        align: "end",
        hideable: false,
        card: "hidden",
        cell: (e) => (
          <span className="inline-flex items-center gap-0.5">
            <IconButton variant="ghost" size="sm" label={w.actions.log} onClick={() => void hook.openLog(e)}>
              <EyeIcon size={15} />
            </IconButton>
            <IconButton
              variant="ghost"
              size="sm"
              label={w.actions.rotate}
              disabled={hook.isSaving}
              onClick={() => {
                if (window.confirm(w.rotateConfirm)) void hook.rotate(e);
              }}
            >
              <Icon name="key" size={15} />
            </IconButton>
            <IconButton
              variant="ghost"
              size="sm"
              label={w.actions.delete}
              onClick={() => {
                if (window.confirm(w.deleteConfirm)) void hook.remove(e);
              }}
            >
              <TrashIcon size={15} />
            </IconButton>
          </span>
        ),
      },
    ],
    // hook.* are stable per render of this component; the table only needs the words and the locale.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [w, dateLocale, hook.isSaving],
  );

  const labels = {
    loading: w.loading,
    empty: w.emptyTitle,
    details: t.jobsTable.details,
    expandRow: (name: string) => fill(t.jobsTable.expandRow, { name }),
    showMore: (name: string) => fill(t.jobsTable.showMore, { name }),
    results: (n: number) => (n === 1 ? t.jobsTable.resultsOne : fill(t.jobsTable.results, { n })),
    filters: t.jobsTable.filters,
    clearFilters: t.jobsTable.clearFilters,
    removeFilter: (label: string) => fill(t.jobsTable.removeFilter, { label }),
    activeFilter: (header: string, value: string) => fill(t.jobsTable.activeFilter, { header, value }),
    search: (header: string) => fill(t.jobsTable.search, { header }),
  };

  return (
    <SettingsSection
      variant="flat"
      id="webhooks"
      title={w.title}
      description={w.subtitle}
      footer={
        <Button type="button" size="sm" variant="secondary" onClick={() => setCreateOpen(true)}>
          {w.create}
        </Button>
      }
    >
      <DataTable
        caption={w.caption}
        rows={hook.endpoints}
        columns={columns}
        getRowId={(e) => e.id}
        rowLabel={(e) => e.url}
        loading={hook.isLoading}
        empty={
          <div className="space-y-1 p-6 text-center">
            <p className="text-sm font-medium text-fg">{w.emptyTitle}</p>
            <p className="text-xs text-fg-muted">{w.emptyDesc}</p>
          </div>
        }
        labels={labels}
      />
      <p className="py-3 text-xs text-fg-muted">{w.verifyHint}</p>

      <Dialog.Root
        open={isCreateOpen}
        onOpenChange={(next) => {
          if (!next) {
            setCreateOpen(false);
            hook.clearError();
          }
        }}
      >
        <Dialog.Content closeLabel={w.cancel}>
          <Dialog.Header>
            <Dialog.Title>{w.create}</Dialog.Title>
            <Dialog.Description>{w.urlHelp}</Dialog.Description>
          </Dialog.Header>
          <Dialog.Body>
            <CreateWebhookForm
              error={hook.error}
              onSubmit={async (url, events) => {
                if (await hook.create(url, events)) setCreateOpen(false);
              }}
            />
          </Dialog.Body>
        </Dialog.Content>
      </Dialog.Root>

      <Dialog.Root open={hook.revealed !== null} onOpenChange={(next) => !next && hook.dismissRevealed()}>
        <Dialog.Content closeLabel={w.done}>
          <Dialog.Header>
            <Dialog.Title>{w.revealTitle}</Dialog.Title>
            <Dialog.Description>{hook.revealed?.url}</Dialog.Description>
          </Dialog.Header>
          <Dialog.Body>
            {hook.revealed && <RevealSecret secret={hook.revealed.secret} onDone={hook.dismissRevealed} />}
          </Dialog.Body>
        </Dialog.Content>
      </Dialog.Root>

      <Dialog.Root open={hook.log !== null} onOpenChange={(next) => !next && hook.closeLog()}>
        <Dialog.Content closeLabel={w.close}>
          <Dialog.Header>
            <Dialog.Title>{w.logTitle}</Dialog.Title>
            <Dialog.Description>{hook.log?.endpoint.url}</Dialog.Description>
          </Dialog.Header>
          <Dialog.Body>{hook.log && <DeliveryLog deliveries={hook.log.deliveries} />}</Dialog.Body>
        </Dialog.Content>
      </Dialog.Root>
    </SettingsSection>
  );
}
