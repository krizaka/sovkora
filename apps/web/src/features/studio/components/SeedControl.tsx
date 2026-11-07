"use client";

import { useState } from "react";
import { Field, Input } from "@krizaka/ui/field";
import { Switch } from "@krizaka/ui/switch";
import { useTranslation } from "@/core/context/LocaleContext";
import type { InputField } from "@/features/studio/hooks/useInputSchema";

interface SeedControlProps {
  field: InputField;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}

/** The seed: random by default (nothing is sent); turn the toggle off to type the number that repeats a result. */
export function SeedControl({ field, value, disabled, onChange }: Readonly<SeedControlProps>) {
  const { t } = useTranslation();
  const form = t.studio.form;
  const [fixed, setFixed] = useState(value !== "");
  const inputId = `studio-run-${field.key}`;
  return (
    <Field.Root className="gap-1.5">
      <Field.Label htmlFor={inputId}>{field.title}</Field.Label>
      <div className="flex items-center gap-3">
        <Switch
          label={form.seedRandom}
          checked={!fixed}
          disabled={disabled}
          onCheckedChange={(random) => {
            setFixed(!random);
            if (random) onChange("");
          }}
        />
        <span className="text-sm text-fg">{form.seedRandom}</span>
        {fixed && (
          <Input
            id={inputId}
            inputMode="numeric"
            placeholder={form.seedPlaceholder}
            value={value}
            disabled={disabled}
            maxLength={10}
            onChange={(event) => onChange(event.target.value.replace(/\D/g, ""))}
          />
        )}
      </div>
      <Field.Hint>{form.seedRandomHint}</Field.Hint>
    </Field.Root>
  );
}
