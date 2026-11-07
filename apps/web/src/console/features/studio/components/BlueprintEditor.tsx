"use client";

import { useState } from "react";
import { Button } from "@krizaka/orazaka-design-system";
import { StudioAdminApi } from "@/console/services/studio.admin.api";

interface BlueprintEditorProps {
  studioKey: string;
  featureKeys: string[];
  onSaved: () => void;
}

/** A minimal, valid starting point so the author edits rather than stares at a blank box. */
const STARTER_DEFINITION = `{
  "steps": [
    {
      "id": "describe",
      "kind": "CAPABILITY",
      "featureKey": "orazaka.core.media.vision",
      "forEach": "{{inputs.photos}}",
      "maxParallel": 3,
      "inputs": { "assetId": "{{item}}", "prompt": "Décris cette photo. Ton: {{config.tone}}." },
      "out": "descriptions",
      "onError": "SKIP",
      "timeout": "PT2M"
    }
  ],
  "outputs": [
    { "key": "descriptions", "label": "Descriptions", "from": "{{steps.descriptions}}", "type": "TEXT" }
  ]
}`;

const STARTER_INPUT_SCHEMA = `{
  "type": "object",
  "required": ["photos"],
  "properties": {
    "photos": { "type": "array", "items": { "type": "string" }, "title": "Photos" }
  }
}`;

const STARTER_CONFIG_SCHEMA = `{
  "tone": { "enum": ["premium", "chaleureux", "direct"], "default": "premium" }
}`;

/**
 * Authors one blueprint version.
 *
 * The editor is deliberately a JSON surface rather than a drag-and-drop canvas. The
 * blueprint IS the product artefact — it is reviewed, diffed and versioned — and a
 * visual builder that round-trips through a lossy model would make the stored JSON
 * something nobody can trust reading.
 *
 * Validation is the server's: it owns the graph rules, so a second copy here would
 * eventually disagree about what publishes. What this screen guarantees is that the
 * refusal is shown verbatim, naming the offending step.
 */
export function BlueprintEditor({ studioKey, featureKeys, onSaved }: Readonly<BlueprintEditorProps>) {
  const [version, setVersion] = useState("");
  const [definition, setDefinition] = useState(STARTER_DEFINITION);
  const [inputSchema, setInputSchema] = useState(STARTER_INPUT_SCHEMA);
  const [configSchema, setConfigSchema] = useState(STARTER_CONFIG_SCHEMA);
  const [estimatedCredits, setEstimatedCredits] = useState("80");
  const [changelog, setChangelog] = useState("");
  const [isBusy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const act = async (action: () => Promise<void>, success: string) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(success);
      onSaved();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Échec");
    } finally {
      setBusy(false);
    }
  };

  const save = () =>
    act(
      () =>
        StudioAdminApi.saveDraft(studioKey, version, {
          definition,
          inputSchema,
          configSchema,
          estimatedCredits: Number(estimatedCredits) || 0,
          changelog,
        }),
      `Brouillon ${version} enregistré et validé.`,
    );

  const publish = () => act(() => StudioAdminApi.publish(studioKey, version), `Version ${version} publiée.`);

  const canAct = version.trim() !== "" && !isBusy;

  return (
    <section className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="flex flex-col gap-1">
          <span className="text-xs text-fg-muted">Version (semver)</span>
          <input
            value={version}
            onChange={(event) => setVersion(event.target.value)}
            placeholder="1.1.0"
            disabled={isBusy}
            className="h-9 rounded border border-border-subtle bg-surface-2 px-2 text-sm text-fg disabled:opacity-50"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-fg-muted">Crédits estimés / exécution</span>
          <input
            value={estimatedCredits}
            onChange={(event) => setEstimatedCredits(event.target.value)}
            inputMode="numeric"
            disabled={isBusy}
            className="h-9 rounded border border-border-subtle bg-surface-2 px-2 text-sm text-fg disabled:opacity-50"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-fg-muted">Note de version</span>
          <input
            value={changelog}
            onChange={(event) => setChangelog(event.target.value)}
            placeholder="Ce qui change pour l'utilisateur"
            disabled={isBusy}
            className="h-9 rounded border border-border-subtle bg-surface-2 px-2 text-sm text-fg disabled:opacity-50"
          />
        </label>
      </div>

      <details className="rounded border border-border-subtle p-3">
        <summary className="cursor-pointer text-xs text-fg-muted">
          Capacités disponibles ({featureKeys.length}) — un step CAPABILITY doit en nommer une
        </summary>
        <ul className="mt-2 flex flex-wrap gap-1.5">
          {featureKeys.map((key) => (
            <li key={key} className="rounded bg-surface-2 px-2 py-0.5 font-mono text-[11px] text-fg-secondary">
              {key}
            </li>
          ))}
        </ul>
      </details>

      <JsonField
        label="Définition (steps + outputs)"
        value={definition}
        onChange={setDefinition}
        disabled={isBusy}
        rows={18}
      />
      <JsonField
        label="Schéma des entrées (formulaire d'exécution)"
        value={inputSchema}
        onChange={setInputSchema}
        disabled={isBusy}
        rows={8}
      />
      <JsonField
        label="Schéma de configuration (dialogue d'installation)"
        value={configSchema}
        onChange={setConfigSchema}
        disabled={isBusy}
        rows={6}
      />

      {error && <p className="whitespace-pre-wrap text-sm text-fg-danger">{error}</p>}
      {notice && <p className="text-sm text-fg-success">{notice}</p>}

      <div className="flex gap-2">
        <Button onClick={() => void save()} disabled={!canAct}>
          Enregistrer le brouillon
        </Button>
        <Button onClick={() => void publish()} disabled={!canAct} variant="secondary">
          Publier
        </Button>
      </div>
    </section>
  );
}

interface JsonFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
  rows: number;
}

/** One JSON pane. A sub-component so the editor above stays a layout. */
function JsonField({ label, value, onChange, disabled, rows }: Readonly<JsonFieldProps>) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-xs text-fg-muted">{label}</span>
      <textarea
        value={value}
        rows={rows}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        spellCheck={false}
        className="rounded border border-border-subtle bg-surface-2 p-2 font-mono text-[12px] leading-relaxed text-fg disabled:opacity-50"
      />
    </label>
  );
}
