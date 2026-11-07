"use client";

import * as React from "react";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Switch } from "@krizaka/ui/switch";
import { Tabs } from "@krizaka/ui/tabs";
import { DataTable, DataTableFilters, type DataTableColumn } from "@krizaka/ui/data-table";
import { consoleCopy } from "@/console/core/copy/console";
import type { Capability, ModelEntry } from "@/console/services/platform.schemas";
import { useCatalogue } from "@/console/features/catalogue/hooks/useCatalogue";
import { providerSummaries, type ProviderSummary } from "@/console/features/catalogue/utils/catalogue";

const m = consoleCopy.models;

const routeCell = (c: Capability) => <code className="text-xs">{c.routingKey}</code>;

const modelColumns: DataTableColumn<ModelEntry>[] = [
  {
    id: "modelName",
    header: m.model,
    sortable: true,
    filter: "text",
    card: "title",
    cell: (x) => x.modelLabel ?? x.modelName,
  },
  { id: "category", header: m.category, sortable: true, filter: "facet" },
  { id: "providerName", header: m.provider, filter: "facet", value: (x) => x.providerName ?? "unassigned" },
  {
    id: "compatible",
    header: m.host,
    value: (x) => (x.compatible ? m.compatible : m.incompatible),
    cell: (x) => (
      <Badge tone={x.compatible ? "success" : "warning"}>{x.compatible ? m.compatible : m.incompatible}</Badge>
    ),
  },
  {
    id: "isDefault",
    header: m.isDefault,
    value: (x) => (x.isDefault ? "yes" : ""),
    cell: (x) => (x.isDefault ? <Badge tone="accent">{m.isDefault}</Badge> : null),
  },
];

const providerColumns: DataTableColumn<ProviderSummary>[] = [
  { id: "provider", header: m.provider, sortable: true, card: "title" },
  { id: "models", header: m.models_count, sortable: true, align: "end" },
  { id: "categories", header: m.category, value: (p) => p.categories.join(", ") },
];

/** The models registry, its providers and the capabilities an administrator may switch off. */
export function CatalogueScreen() {
  const data = useCatalogue();
  const providers = React.useMemo(() => providerSummaries(data.models, data.providers), [data.models, data.providers]);

  const capabilityColumns = React.useMemo<DataTableColumn<Capability>[]>(
    () => [
      {
        id: "featureKey",
        header: m.capability,
        sortable: true,
        filter: "text",
        card: "title",
        cell: (c) => c.featureKey.replace(/^orazaka\./, ""),
      },
      { id: "handlerKey", header: m.handler, cell: (c) => c.handlerKey },
      { id: "routingKey", header: m.route, cell: routeCell },
      { id: "billableCapability", header: m.billable, filter: "facet", value: (c) => c.billableCapability ?? "—" },
      {
        id: "enabled",
        header: m.enabled,
        value: (c) => (c.enabled ? "on" : "off"),
        cell: (c) => (
          <Switch
            label={`${m.switchLabel} ${c.featureKey}`}
            checked={c.enabled}
            onCheckedChange={(on) => void data.toggle(c.featureKey, on)}
          />
        ),
      },
    ],
    // `toggle` is stable (useCallback with no dependencies).
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [data.toggle],
  );

  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{m.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{m.lead}</p>
      </header>
      {data.hasError && !data.isLoading && (
        <Alert
          tone="warning"
          action={
            <Button variant="secondary" size="sm" onClick={data.reload}>
              {m.retry}
            </Button>
          }
        >
          {m.error}
        </Alert>
      )}
      {data.switchFailed && <Alert tone="danger">{m.toggleFailed}</Alert>}
      <Tabs.Root routeKey="tab" routeValues={["models", "providers", "capabilities"]}>
        <Tabs.List aria-label={m.title} className="overflow-x-auto">
          <Tabs.Trigger value="models">{m.tabs.models}</Tabs.Trigger>
          <Tabs.Trigger value="providers">{m.tabs.providers}</Tabs.Trigger>
          <Tabs.Trigger value="capabilities">{m.tabs.capabilities}</Tabs.Trigger>
        </Tabs.List>
        <Tabs.Content value="models">
          <DataTable
            caption={m.tabs.models}
            rows={data.models}
            columns={modelColumns}
            getRowId={(x) => x.modelName}
            loading={data.isLoading}
            defaultPageSize={15}
            toolbar={<DataTableFilters />}
          />
        </Tabs.Content>
        <Tabs.Content value="providers">
          <p className="mb-3 text-sm text-fg-secondary">{m.providersLead}</p>
          <DataTable
            caption={m.tabs.providers}
            rows={providers}
            columns={providerColumns}
            getRowId={(p) => p.provider}
            loading={data.isLoading}
          />
        </Tabs.Content>
        <Tabs.Content value="capabilities">
          <DataTable
            caption={m.tabs.capabilities}
            rows={data.capabilities}
            columns={capabilityColumns}
            getRowId={(c) => c.featureKey}
            loading={data.isLoading}
            defaultPageSize={15}
            toolbar={<DataTableFilters />}
          />
        </Tabs.Content>
      </Tabs.Root>
    </article>
  );
}
