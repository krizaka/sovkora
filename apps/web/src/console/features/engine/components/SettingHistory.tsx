"use client";

import { Alert } from "@krizaka/ui/alert";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import type { EngineWords } from "@/console/core/copy/engine";
import type { RuntimeAuditEntry, RuntimeSetting } from "@/console/services/runtime-config.admin.api";
import { useAudit } from "@/console/features/engine/hooks/useEngine";

const historyColumns = (words: EngineWords): DataTableColumn<RuntimeAuditEntry>[] => [
  { id: "at", header: words.at, card: "title", cell: (e) => new Date(e.at).toLocaleString() },
  { id: "actorId", header: words.actor, cell: (e) => <code className="text-xs">{e.actorId ?? words.empty}</code> },
  { id: "before", header: words.before, cell: (e) => e.before ?? words.empty },
  { id: "after", header: words.after, cell: (e) => e.after ?? words.empty },
];

/** The change history of one setting, newest first — actor ids only, never names. `version` re-reads it after a save. */
export function SettingHistory({
  setting,
  words,
  version,
}: Readonly<{ setting: RuntimeSetting; words: EngineWords; version: unknown }>) {
  const { entries, isLoading, hasError } = useAudit(setting.key, version);
  const columns = historyColumns(words);
  if (hasError) return <Alert tone="warning">{words.historyError}</Alert>;
  if (!isLoading && entries.length === 0) return <p className="text-sm text-fg-secondary">{words.historyEmpty}</p>;
  return (
    <DataTable
      caption={`${words.history} — ${setting.key}`}
      rows={entries}
      columns={columns}
      getRowId={(e) => `${e.at}|${e.actorId ?? ""}`}
      loading={isLoading}
    />
  );
}
