"use client";

import * as React from "react";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { Field, Input, Textarea } from "@krizaka/ui/field";
import { Switch } from "@krizaka/ui/switch";
import type { EngineWords } from "@/console/core/copy/engine";
import type { RuntimeSetting, SettingValue } from "@/console/services/runtime-config.admin.api";
import { boundsText, display, draftOf, parseDraft } from "@/console/features/engine/utils/engine";
import { SettingHistory } from "./SettingHistory";

/** One setting: its description, current value, default and bounds, edited in place with validation, and its history. */
export function SettingCard({
  setting,
  words,
  onSave,
}: Readonly<{
  setting: RuntimeSetting;
  words: EngineWords;
  onSave: (key: string, value: SettingValue) => Promise<string | null>;
}>) {
  const [draft, setDraft] = React.useState(draftOf(setting.value));
  const draftRef = React.useRef(draft);
  const inFlight = React.useRef(false);
  const [error, setError] = React.useState<string | null>(null);
  const [status, setStatus] = React.useState<"idle" | "saving" | "saved">("idle");
  const [showHistory, setShowHistory] = React.useState(false);
  const id = `setting-${setting.key}`;
  const dirty = draft !== draftOf(setting.value);
  const bounds = boundsText(setting, words);
  const describedBy = error !== null ? `${id}-msg` : undefined;

  const change = (next: string) => {
    if (inFlight.current) return;
    draftRef.current = next;
    setDraft(next);
    setError(null);
    setStatus("idle");
  };

  const submit = async () => {
    if (inFlight.current) return;
    const parsed = parseDraft(setting, draft, words);
    if (!parsed.ok) return setError(parsed.error);
    inFlight.current = true;
    setStatus("saving");
    try {
      let refusal: string | null;
      try {
        refusal = await onSave(setting.key, parsed.value);
      } catch {
        refusal = "";
      }
      setError(refusal === null ? null : refusal || words.failed);
      setStatus(refusal === null ? "saved" : "idle");
      if (refusal === null && draftRef.current === draft) {
        const canonical = draftOf(parsed.value);
        draftRef.current = canonical;
        setDraft(canonical);
      }
    } finally {
      inFlight.current = false;
    }
  };

  return (
    <Card.Root className="p-5">
      <div data-setting={setting.key} className="flex flex-col gap-3">
        <header className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-mono text-sm font-semibold text-fg">{setting.key}</h3>
          <Badge tone="neutral">{setting.type}</Badge>
        </header>
        <p className="text-sm text-fg-secondary">{setting.description}</p>
        <dl className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-fg-muted">
          <div>
            <dt className="inline">{words.current}: </dt>
            <dd className="inline text-fg">{display(setting.value, words)}</dd>
          </div>
          <div>
            <dt className="inline">{words.defaultValue}: </dt>
            <dd className="inline text-fg">{display(setting.default, words)}</dd>
          </div>
          {bounds && (
            <div>
              <dt className="inline">{words.bounds}: </dt>
              <dd className="inline text-fg">{bounds}</dd>
            </div>
          )}
        </dl>
        <Field.Root>
          {setting.type === "boolean" ? (
            <Switch
              label={setting.key}
              checked={draft === "true"}
              disabled={status === "saving"}
              onCheckedChange={(on) => change(String(on))}
            />
          ) : (
            <>
              <Field.Label htmlFor={id}>{setting.key}</Field.Label>
              {setting.type === "list" ? (
                <Textarea
                  id={id}
                  rows={4}
                  value={draft}
                  invalid={error !== null}
                  disabled={status === "saving"}
                  aria-describedby={describedBy}
                  onChange={(e) => change(e.target.value)}
                />
              ) : (
                <Input
                  id={id}
                  inputMode="numeric"
                  onKeyDown={(e) => e.key === "Enter" && dirty && void submit()}
                  value={draft}
                  invalid={error !== null}
                  disabled={status === "saving"}
                  aria-describedby={describedBy}
                  onChange={(e) => change(e.target.value)}
                />
              )}
            </>
          )}
          {setting.type === "list" && <Field.Hint>{words.listHint}</Field.Hint>}
          {/* Only when there is something to say: an empty Field.Error still draws its marker under every field. */}
          {error !== null && <Field.Error id={`${id}-msg`}>{error}</Field.Error>}
        </Field.Root>
        {status === "saved" && <output className="text-sm text-success">{words.saved}</output>}
        <footer className="flex flex-wrap gap-2">
          <Button type="button" size="sm" onClick={() => void submit()} disabled={!dirty || status === "saving"}>
            {status === "saving" ? words.saving : words.save}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="secondary"
            onClick={() => change(draftOf(setting.default))}
            disabled={status === "saving"}
          >
            {words.reset}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            aria-expanded={showHistory}
            onClick={() => setShowHistory((v) => !v)}
          >
            {showHistory ? words.hideHistory : words.history}
          </Button>
        </footer>
        {showHistory && <SettingHistory setting={setting} words={words} version={setting.value} />}
      </div>
    </Card.Root>
  );
}
