"use client";

import * as React from "react";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { Field, Input } from "@krizaka/ui/field";
import { fill } from "@/console/core/copy/engine";
import type { FeatureToolsWords } from "@/console/core/copy/featureTools";
import { parseRounds } from "@/console/features/engine/utils/featureTools";

export type Range = { min: number; max: number };
/** Which bound a row edits: the tool rounds of a turn, or the answers of a structured-output call. */
export type BoundKind = "tools" | "structured";
export type Save = (featureKey: string, rounds: number) => Promise<string | null>;

/** One bound, edited in place: the number typed is checked against the server's range before it is sent. */
export function BoundRow({
  bound,
  value,
  kind = "tools",
  range,
  words,
  onSave,
  onRemove,
}: Readonly<{
  bound: { featureKey: string; isDefault: boolean };
  value: number;
  kind?: BoundKind;
  range: Range;
  words: FeatureToolsWords;
  onSave: Save;
  onRemove: (featureKey: string) => Promise<string | null>;
}>) {
  const [draft, setDraft] = React.useState(String(value));
  const [error, setError] = React.useState<string | null>(null);
  const [status, setStatus] = React.useState<"idle" | "saving" | "saved">("idle");
  const id = `${kind === "tools" ? "bound" : kind}-${bound.featureKey}`;
  const dirty = draft !== String(value);

  const submit = async () => {
    const parsed = parseRounds(draft, range, words);
    if (!parsed.ok) {
      return setError(parsed.error);
    }
    setStatus("saving");
    const refusal = await onSave(bound.featureKey, parsed.value);
    setError(refusal === null ? null : refusal || words.failed);
    setStatus(refusal === null ? "saved" : "idle");
  };

  const remove = async () => {
    setStatus("saving");
    const refusal = await onRemove(bound.featureKey);
    setError(refusal === null ? null : refusal || words.failed);
    setStatus("idle");
  };

  return (
    <Card.Root className="p-5">
      <div
        data-bound={kind === "tools" ? bound.featureKey : undefined}
        data-structured-bound={kind === "structured" ? bound.featureKey : undefined}
        className="flex flex-col gap-3"
      >
        <header className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-mono text-sm font-semibold text-fg">
            {bound.isDefault ? words.defaultRow : bound.featureKey}
          </h3>
          {bound.isDefault && <Badge tone="neutral">*</Badge>}
        </header>
        <Field.Root>
          <Field.Label htmlFor={id}>{words.rounds}</Field.Label>
          <Input
            id={id}
            inputMode="numeric"
            value={draft}
            invalid={error !== null}
            disabled={status === "saving"}
            aria-describedby={error !== null ? `${id}-msg` : undefined}
            onKeyDown={(e) => e.key === "Enter" && dirty && void submit()}
            onChange={(e) => {
              setDraft(e.target.value);
              setError(null);
              setStatus("idle");
            }}
          />
          <Field.Hint>{fill(words.range, range)}</Field.Hint>
          {error !== null && <Field.Error id={`${id}-msg`}>{error}</Field.Error>}
        </Field.Root>
        {status === "saved" && <output className="text-sm text-success">{words.saved}</output>}
        <footer className="flex flex-wrap gap-2">
          <Button type="button" size="sm" onClick={() => void submit()} disabled={!dirty || status === "saving"}>
            {status === "saving" ? words.saving : words.save}
          </Button>
          {!bound.isDefault && (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={() => void remove()}
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
