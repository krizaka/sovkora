import { useState } from "react";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { Field, Input } from "@krizaka/ui/field";
import { Switch } from "@krizaka/ui/switch";
import { api, errorMessage, type Settings, type Snapshot } from "../lib/api";
import { useI18n } from "../lib/i18n";

export function SettingsPanel({ snap, onSaved, refresh }: { snap: Snapshot; onSaved: (s: Snapshot) => void; refresh: () => Promise<void> }) {
  const { t } = useI18n();
  const [form, setForm] = useState<Settings>(snap.settings);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setError(null);
    setMessage(null);
    try {
      const saved = await api.saveSettings(form);
      onSaved({ ...snap, settings: saved });
      setForm(saved);
      setMessage(t("common.saved"));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="max-w-2xl space-y-6">
      <h2 className="text-lg font-semibold">{t("settings.title")}</h2>

      <fieldset className="space-y-3">
        <legend className="mb-1 text-sm font-semibold">{t("settings.connection")}</legend>
        {(["remote", "local"] as const).map((m) => (
          <label key={m} className="flex items-center gap-3 text-sm">
            <input type="radio" name="mode" checked={form.mode === m} onChange={() => setForm({ ...form, mode: m })} className="accent-[var(--color-accent)]" />
            {m === "remote" ? t("settings.modeRemote") : t("settings.modeLocal")}
          </label>
        ))}
        {form.mode === "remote" && (
          <Field.Root>
            <Field.Label htmlFor="remote-url">{t("settings.remoteUrl")}</Field.Label>
            <Input id="remote-url" inputMode="url" spellCheck={false} value={form.remoteUrl} onChange={(e) => setForm({ ...form, remoteUrl: e.target.value })} />
            <Field.Hint>{t("settings.remoteHelp")}</Field.Hint>
          </Field.Root>
        )}
      </fieldset>

      <Field.Root>
        <Field.Label htmlFor="workspace-dir">{t("settings.workspaceDir")}</Field.Label>
        <Input id="workspace-dir" spellCheck={false} placeholder="~/krizaka-com/products/orazaka" value={form.workspaceDir ?? ""} onChange={(e) => setForm({ ...form, workspaceDir: e.target.value || null })} />
        <Field.Hint>{t("settings.workspaceHelp")}</Field.Hint>
      </Field.Root>

      <div className="flex items-start gap-3">
        <Switch
          label={t("settings.startup")}
          checked={snap.autostart}
          onCheckedChange={(on) => void api.setAutostart(on).then(refresh).catch((e) => setError(errorMessage(e)))}
        />
        <div>
          <p className="text-sm font-medium">{t("settings.startup")}</p>
          <p className="text-xs text-fg-secondary">{t("settings.startupHelp")}</p>
        </div>
      </div>

      {error && <Alert tone="danger">{error}</Alert>}
      <div className="flex items-center gap-3">
        <Button onClick={() => void save()}>{t("common.save")}</Button>
        {message && <output className="text-sm text-fg-secondary">{message}</output>}
      </div>
      <p className="border-t border-border-subtle pt-4 text-xs text-fg-secondary">{t("settings.about")} · {snap.appVersion}</p>
    </div>
  );
}
