"use client";

import * as React from "react";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { Field, Input } from "@krizaka/ui/field";
import type { FeatureToolsWords } from "@/console/core/copy/featureTools";
import { parseRounds } from "@/console/features/engine/utils/featureTools";
import type { BoundKind, Range, Save } from "./BoundRow";

/** Adds the bound of a feature that has none yet. */
export function AddBound({
  range,
  words,
  onSave,
  kind = "tools",
}: Readonly<{
  range: Range;
  words: FeatureToolsWords;
  onSave: Save;
  kind?: BoundKind;
}>) {
  const prefix = kind === "tools" ? "bound-new" : `${kind}-new`;
  const [key, setKey] = React.useState("");
  const [rounds, setRounds] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  const submit = async () => {
    const featureKey = key.trim();
    if (featureKey === "") {
      return setError(words.invalidKey);
    }
    const parsed = parseRounds(rounds, range, words);
    if (!parsed.ok) {
      return setError(parsed.error);
    }
    setBusy(true);
    const refusal = await onSave(featureKey, parsed.value);
    setBusy(false);
    setError(refusal === null ? null : refusal || words.failed);
    if (refusal === null) {
      setKey("");
      setRounds("");
    }
  };

  return (
    <Card.Root className="p-5">
      <div className="flex flex-col gap-3">
        <h3 className="text-sm font-semibold text-fg">{words.addTitle}</h3>
        <Field.Root>
          <Field.Label htmlFor={`${prefix}-key`}>{words.featureKey}</Field.Label>
          <Input
            id={`${prefix}-key`}
            value={key}
            disabled={busy}
            onChange={(e) => {
              setKey(e.target.value);
              setError(null);
            }}
          />
          <Field.Hint>{words.featureKeyHint}</Field.Hint>
        </Field.Root>
        <Field.Root>
          <Field.Label htmlFor={`${prefix}-rounds`}>{words.rounds}</Field.Label>
          <Input
            id={`${prefix}-rounds`}
            inputMode="numeric"
            value={rounds}
            disabled={busy}
            invalid={error !== null}
            aria-describedby={error !== null ? `${prefix}-msg` : undefined}
            onChange={(e) => {
              setRounds(e.target.value);
              setError(null);
            }}
          />
          {error !== null && <Field.Error id={`${prefix}-msg`}>{error}</Field.Error>}
        </Field.Root>
        <footer>
          <Button type="button" size="sm" onClick={() => void submit()} disabled={busy}>
            {words.add}
          </Button>
        </footer>
      </div>
    </Card.Root>
  );
}
