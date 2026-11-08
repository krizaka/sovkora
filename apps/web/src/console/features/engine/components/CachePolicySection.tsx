"use client";

import * as React from "react";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { Field, Input } from "@krizaka/ui/field";
import { RadioGroup } from "@krizaka/ui/radio-group";
import type { FeatureToolsWords } from "@/console/core/copy/featureTools";
import type { CachePolicies } from "@/console/services/feature-tools.admin.api";

type Save = (featureKey: string, strategy: string) => Promise<string | null>;

/** The words of one strategy, from the closed set the server lists; an unknown one is shown as the server wrote it. */
const strategyWords = (words: FeatureToolsWords, strategy: string): string =>
  (words as unknown as Record<string, string>)[`cache${strategy}`] ?? strategy;

/** The radios of one policy: a choice among the strategies the server offers. */
function Strategies({
  id,
  strategies,
  value,
  words,
  disabled,
  invalid,
  onChange,
}: Readonly<{
  id: string;
  strategies: readonly string[];
  value: string;
  words: FeatureToolsWords;
  disabled: boolean;
  invalid: boolean;
  onChange: (strategy: string) => void;
}>) {
  return (
    <RadioGroup.Root label={words.cacheStrategy} value={value} invalid={invalid} onValueChange={onChange}>
      {strategies.map((s) => (
        <RadioGroup.Item key={s} id={`${id}-${s}`} value={s} disabled={disabled}>
          {strategyWords(words, s)}
        </RadioGroup.Item>
      ))}
    </RadioGroup.Root>
  );
}

/** One policy, edited in place; saving or removing answers with the server's own refusal, if any. */
function PolicyRow({
  policy,
  strategies,
  words,
  onSave,
  onRemove,
}: Readonly<{
  policy: { featureKey: string; strategy: string; isDefault: boolean };
  strategies: readonly string[];
  words: FeatureToolsWords;
  onSave: Save;
  onRemove: (featureKey: string) => Promise<string | null>;
}>) {
  const [draft, setDraft] = React.useState(policy.strategy);
  const [error, setError] = React.useState<string | null>(null);
  const [status, setStatus] = React.useState<"idle" | "saving" | "saved">("idle");
  const id = `cache-${policy.featureKey}`;

  const run = async (action: () => Promise<string | null>, done: "idle" | "saved") => {
    setStatus("saving");
    const refusal = await action();
    setError(refusal === null ? null : refusal || words.failed);
    setStatus(refusal === null ? done : "idle");
  };

  return (
    <Card.Root className="p-5">
      <div data-cache-policy={policy.featureKey} className="flex flex-col gap-3">
        <header className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-mono text-sm font-semibold text-fg">
            {policy.isDefault ? words.cacheDefaultRow : policy.featureKey}
          </h3>
          {policy.isDefault && <Badge tone="neutral">*</Badge>}
        </header>
        <Strategies
          id={id}
          strategies={strategies}
          value={draft}
          words={words}
          disabled={status === "saving"}
          invalid={error !== null}
          onChange={(s) => {
            setDraft(s);
            setError(null);
            setStatus("idle");
          }}
        />
        {error !== null && <Field.Error id={`${id}-msg`}>{error}</Field.Error>}
        {status === "saved" && <output className="text-sm text-success">{words.cacheSaved}</output>}
        <footer className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            onClick={() => void run(() => onSave(policy.featureKey, draft), "saved")}
            disabled={draft === policy.strategy || status === "saving"}
          >
            {status === "saving" ? words.saving : words.save}
          </Button>
          {!policy.isDefault && (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={() => void run(() => onRemove(policy.featureKey), "idle")}
              disabled={status === "saving"}
            >
              {words.remove}
            </Button>
          )}
        </footer>
      </div>
    </Card.Root>
  );
}

/** Sets the policy of a feature that has none yet. */
function AddPolicy({
  strategies,
  words,
  onSave,
}: Readonly<{ strategies: readonly string[]; words: FeatureToolsWords; onSave: Save }>) {
  const [key, setKey] = React.useState("");
  const [strategy, setStrategy] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  const submit = async () => {
    const featureKey = key.trim();
    if (featureKey === "") {
      return setError(words.invalidKey);
    }
    if (strategy === "") {
      return setError(words.cacheChoose);
    }
    setBusy(true);
    const refusal = await onSave(featureKey, strategy);
    setBusy(false);
    setError(refusal === null ? null : refusal || words.failed);
    if (refusal === null) {
      setKey("");
      setStrategy("");
    }
  };

  return (
    <Card.Root className="p-5">
      <div className="flex flex-col gap-3">
        <h3 className="text-sm font-semibold text-fg">{words.cacheAddTitle}</h3>
        <Field.Root>
          <Field.Label htmlFor="cache-new-key">{words.featureKey}</Field.Label>
          <Input
            id="cache-new-key"
            value={key}
            disabled={busy}
            invalid={error !== null}
            aria-describedby={error !== null ? "cache-new-msg" : undefined}
            onChange={(e) => {
              setKey(e.target.value);
              setError(null);
            }}
          />
          <Field.Hint>{words.featureKeyHint}</Field.Hint>
        </Field.Root>
        <Strategies
          id="cache-new"
          strategies={strategies}
          value={strategy}
          words={words}
          disabled={busy}
          invalid={error !== null}
          onChange={(s) => {
            setStrategy(s);
            setError(null);
          }}
        />
        {error !== null && <Field.Error id="cache-new-msg">{error}</Field.Error>}
        <footer>
          <Button type="button" size="sm" onClick={() => void submit()} disabled={busy}>
            {words.cacheAdd}
          </Button>
        </footer>
      </div>
    </Card.Root>
  );
}

/** The prompt-cache policies per feature, edited in place by the administrator (ADR-094 §4.5). */
export function CachePolicySection({
  cache,
  words,
  onSave,
  onRemove,
}: Readonly<{
  cache: CachePolicies;
  words: FeatureToolsWords;
  onSave: Save;
  onRemove: (featureKey: string) => Promise<string | null>;
}>) {
  return (
    <section aria-labelledby="cache-policies" className="flex flex-col gap-3">
      <h2 id="cache-policies" className="text-lg font-semibold text-fg">
        {words.cacheTitle}
      </h2>
      <p className="max-w-3xl text-sm text-fg-secondary">{words.cacheLead}</p>
      <div className="grid gap-4 lg:grid-cols-2">
        {cache.policies.map((p) => (
          <PolicyRow
            key={p.featureKey}
            policy={p}
            strategies={cache.strategies}
            words={words}
            onSave={onSave}
            onRemove={onRemove}
          />
        ))}
        <AddPolicy strategies={cache.strategies} words={words} onSave={onSave} />
      </div>
    </section>
  );
}
