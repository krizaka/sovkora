import { useState } from "react";
import { useQuery } from "@krizaka/query";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { Field, Input, Select } from "@krizaka/ui/field";
import { Switch } from "@krizaka/ui/switch";
import { automationsApi, baseName, errorMessage, type Defaults } from "../../lib/api";
import { useI18n } from "../../lib/i18n";

const WEEKDAYS = [1, 2, 3, 4, 5];

/** "Summarise new PDFs every morning" — the template of slice 1, on a folder shared in Files. */
export function NewAutomationForm({ roots, defaults, onCancel, onCreated }: Readonly<{ roots: string[]; defaults: Defaults; onCancel: () => void; onCreated: () => void }>) {
  const { t } = useI18n();
  const [name, setName] = useState(t("automations.form.nameDefault"));
  const [folder, setFolder] = useState(roots[0] ?? "");
  const [time, setTime] = useState(defaults.time);
  const [weekdays, setWeekdays] = useState(false);
  const [baseUrl, setBaseUrl] = useState(defaults.model.baseUrl);
  const [model, setModel] = useState(defaults.model.model);
  const [dryRun, setDryRun] = useState(defaults.dryRun);
  const [error, setError] = useState<string | null>(null);

  // The models of the server typed in the field: keyed by its address, so an answer for an address since replaced never shows.
  const modelsQuery = useQuery({ queryKey: ["automations", "models", baseUrl], queryFn: () => automationsApi.models(baseUrl), retry: false });
  const models: string[] = modelsQuery.data ?? [];

  async function create() {
    setError(null);
    try {
      await automationsApi.create({ name, folder, time, days: weekdays ? WEEKDAYS : [], model, baseUrl, dryRun });
      onCreated();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <Card.Root tone="elevated" data-testid="new-automation">
      <Card.Body className="space-y-4">
        <div>
          <Card.Title as="h4">{t("automations.templateSummarise")}</Card.Title>
          <Card.Description>{t("automations.templateSummariseLead")}</Card.Description>
        </div>
        <Field.Root>
          <Field.Label htmlFor="auto-name">{t("automations.form.name")}</Field.Label>
          <Input id="auto-name" value={name} onChange={(e) => setName(e.target.value)} />
        </Field.Root>
        <Field.Root>
          <Field.Label htmlFor="auto-folder">{t("automations.form.folder")}</Field.Label>
          {roots.length === 0 ? (
            <Alert tone="info">{t("automations.form.noFolder")}</Alert>
          ) : (
            <Select id="auto-folder" value={folder} onChange={(e) => setFolder(e.target.value)}>
              {roots.map((r) => (
                <option key={r} value={r}>{`${baseName(r)} — ${r}`}</option>
              ))}
            </Select>
          )}
          <Field.Hint>{t("automations.form.folderHint")}</Field.Hint>
        </Field.Root>
        <div className="flex flex-wrap items-end gap-4">
          <Field.Root>
            <Field.Label htmlFor="auto-time">{t("automations.form.time")}</Field.Label>
            <Input id="auto-time" type="time" value={time} onChange={(e) => setTime(e.target.value)} className="w-32" />
          </Field.Root>
          <span className="flex items-center gap-2 pb-2 text-sm">
            <Switch id="auto-weekdays" checked={weekdays} onCheckedChange={setWeekdays} />
            <label htmlFor="auto-weekdays">{t("automations.form.weekdays")}</label>
          </span>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field.Root>
            <Field.Label htmlFor="auto-model">{t("automations.form.model")}</Field.Label>
            {models.length > 0 ? (
              <Select id="auto-model" value={model} onChange={(e) => setModel(e.target.value)}>
                {[...new Set([model, ...models])].map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </Select>
            ) : (
              <Input id="auto-model" spellCheck={false} value={model} onChange={(e) => setModel(e.target.value)} />
            )}
            <Field.Hint>{t("automations.form.modelHint")}</Field.Hint>
          </Field.Root>
          <Field.Root>
            <Field.Label htmlFor="auto-base">{t("automations.form.baseUrl")}</Field.Label>
            <Input id="auto-base" inputMode="url" spellCheck={false} value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
          </Field.Root>
        </div>
        <span className="flex items-start gap-3">
          <Switch id="auto-dry" checked={dryRun} onCheckedChange={setDryRun} />
          <span>
            <label htmlFor="auto-dry" className="text-sm font-medium">{t("automations.form.dryRun")}</label>
            <span className="block text-xs text-fg-secondary">{t("automations.form.dryRunHint")}</span>
          </span>
        </span>
        {error && <Alert tone="danger">{error}</Alert>}
        <div className="flex gap-2">
          <Button variant="primary" shape="cut" data-testid="automation-create" disabled={!folder || !model} onClick={() => void create()}>{t("automations.form.create")}</Button>
          <Button variant="ghost" onClick={onCancel}>{t("automations.form.cancel")}</Button>
        </div>
      </Card.Body>
    </Card.Root>
  );
}
