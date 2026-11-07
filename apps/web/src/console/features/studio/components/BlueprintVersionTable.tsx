"use client";

import { useState } from "react";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { StudioAdminApi } from "@/console/services/studio.admin.api";
import type { BlueprintVersion } from "@krizaka/orazaka-shared";

interface BlueprintVersionTableProps {
  studioKey: string;
  versions: BlueprintVersion[];
  onChanged: () => void;
}

/**
 * The version history, with publish and deprecate.
 *
 * Deprecate never deletes: a run records the version it executed, and removing that
 * row would destroy its reproducibility (ADR-034 §4.6). The button says so.
 */
export function BlueprintVersionTable({ studioKey, versions, onChanged }: Readonly<BlueprintVersionTableProps>) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const act = async (version: string, action: () => Promise<void>) => {
    setBusy(version);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Échec");
    } finally {
      setBusy(null);
    }
  };

  const columns: DataTableColumn<BlueprintVersion>[] = [
    {
      id: "version",
      header: "Version",
      card: "title",
      cell: (version) => <span className="font-mono text-[12px]">{version.version}</span>,
    },
    {
      id: "status",
      header: "Statut",
      cell: (version) => (
        <span className={version.status === "PUBLISHED" ? "text-fg-success" : "text-fg-muted"}>{version.status}</span>
      ),
    },
    { id: "estimatedCredits", header: "Crédits", align: "end" },
    { id: "changelog", header: "Note", value: (version) => version.changelog ?? "—" },
    {
      id: "actions",
      header: "",
      align: "end",
      cell: (version) => (
        <span className="space-x-2">
          {version.status === "DRAFT" && (
            <button
              type="button"
              disabled={busy !== null}
              onClick={() => void act(version.version, () => StudioAdminApi.publish(studioKey, version.version))}
              className="text-xs text-accent disabled:opacity-50"
            >
              Publier
            </button>
          )}
          {version.status === "PUBLISHED" && (
            <button
              type="button"
              disabled={busy !== null}
              title="La version reste exécutable pour les installations qui l'ont épinglée"
              onClick={() => void act(version.version, () => StudioAdminApi.deprecate(studioKey, version.version))}
              className="text-xs text-fg-muted disabled:opacity-50"
            >
              Déprécier
            </button>
          )}
        </span>
      ),
    },
  ];

  if (versions.length === 0) {
    return <p className="text-sm text-fg-muted">Aucune version pour ce Studio.</p>;
  }

  return (
    <section className="space-y-2">
      <h2 className="text-xs text-fg-muted">Versions</h2>
      <DataTable
        caption="Versions"
        rows={versions}
        columns={columns}
        getRowId={(version) => version.version}
        labels={{ loading: "Lecture des versions", empty: "Aucune version pour ce Studio." }}
      />
      {error && <p className="whitespace-pre-wrap text-sm text-fg-danger">{error}</p>}
    </section>
  );
}
