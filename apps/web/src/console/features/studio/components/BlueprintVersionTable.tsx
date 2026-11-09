"use client";

import { useState } from "react";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { studiosCopy, type StudiosLocale, type StudiosWords } from "@/console/core/copy/studios";
import { useConsoleLocale } from "@/console/core/providers/ConsoleLocale";
import { StudioAdminApi } from "@/console/services/studio.admin.api";
import type { BlueprintVersion } from "@krizaka/orazaka-shared";

const versionCodeCell = (v: BlueprintVersion) => <VersionCodeCell version={v.version} />;
const versionStatusCell = (v: BlueprintVersion) => <VersionStatusCell status={v.status} />;

interface BlueprintVersionTableProps {
  studioKey: string;
  versions: BlueprintVersion[];
  onChanged: () => void;
  locale?: StudiosLocale;
}

function VersionCodeCell({ version }: Readonly<{ version: string }>) {
  return <span className="font-mono text-[12px]">{version}</span>;
}

function VersionStatusCell({ status }: Readonly<{ status: string }>) {
  return <span className={status === "PUBLISHED" ? "text-fg-success" : "text-fg-muted"}>{status}</span>;
}

function VersionActionsCell({
  version,
  studioKey,
  busy,
  onAct,
  words,
}: Readonly<{
  version: BlueprintVersion;
  studioKey: string;
  busy: string | null;
  onAct: (v: string, action: () => Promise<void>) => void;
  words: StudiosWords["builder"];
}>) {
  return (
    <span className="space-x-2">
      {version.status === "DRAFT" && (
        <button
          type="button"
          disabled={busy !== null}
          onClick={() => {
            onAct(version.version, () => StudioAdminApi.publish(studioKey, version.version));
          }}
          className="text-xs text-accent disabled:opacity-50"
        >
          {words.publish}
        </button>
      )}
      {version.status === "PUBLISHED" && (
        <button
          type="button"
          disabled={busy !== null}
          title={words.deprecateTitle}
          onClick={() => {
            onAct(version.version, () => StudioAdminApi.deprecate(studioKey, version.version));
          }}
          className="text-xs text-fg-muted disabled:opacity-50"
        >
          {words.deprecate}
        </button>
      )}
    </span>
  );
}

/**
 * The version history, with publish and deprecate.
 *
 * Deprecate never deletes: a run records the version it executed, and removing that
 * row would destroy its reproducibility (ADR-034 §4.6). The button says so.
 */
export function BlueprintVersionTable({
  studioKey,
  versions,
  onChanged,
  locale,
}: Readonly<BlueprintVersionTableProps>) {
  const fromContext = useConsoleLocale();
  const words = studiosCopy[locale ?? fromContext].builder;
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const act = async (version: string, action: () => Promise<void>) => {
    setBusy(version);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : words.failed);
    } finally {
      setBusy(null);
    }
  };

  const columns: DataTableColumn<BlueprintVersion>[] = [
    { id: "version", header: words.versionCol, card: "title", cell: versionCodeCell },
    { id: "status", header: words.status, cell: versionStatusCell },
    { id: "estimatedCredits", header: words.creditsCol, align: "end" },
    { id: "changelog", header: words.note, value: (v) => v.changelog ?? "—" },
    {
      id: "actions",
      header: "",
      align: "end",
      cell: (v) => (
        <VersionActionsCell version={v} studioKey={studioKey} busy={busy} onAct={act} words={words} /> // NOSONAR S6478 — render prop called as a function by DataTable; VersionActionsCell is defined outside; studioKey/busy/act/words are stable refs
      ),
    },
  ];

  if (versions.length === 0) {
    return <p className="text-sm text-fg-muted">{words.emptyVersions}</p>;
  }

  return (
    <section className="space-y-2">
      <h2 className="text-xs text-fg-muted">{words.versions}</h2>
      <DataTable
        caption={words.versions}
        rows={versions}
        columns={columns}
        getRowId={(v) => v.version}
        labels={{ loading: words.loadingVersions, empty: words.emptyVersions }}
      />
      {error && <p className="whitespace-pre-wrap text-sm text-fg-danger">{error}</p>}
    </section>
  );
}
