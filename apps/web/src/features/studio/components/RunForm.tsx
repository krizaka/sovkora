"use client";

import { useCallback, useState } from "react";
import { Button } from "@krizaka/ui/button";
import { useTranslation } from "@/core/context/LocaleContext";
import { RunInput, localized } from "@/features/studio/components/RunInput";
import { useInputSchema, type InputField } from "@/features/studio/hooks/useInputSchema";

interface RunFormProps {
  /** The Studio's key: a shared input key ("voice") reads differently from one Studio to another. */
  studioKey?: string;
  inputSchema: string | null;
  estimatedCredits: number;
  isSubmitting: boolean;
  onSubmit: (inputs: Record<string, unknown>) => void;
}

/**
 * The run form, generated from the blueprint's JSON Schema.
 *
 * Inputs stay blocked while a run is being submitted (ERR-126): starting a second
 * run costs a second credit hold, so a user who cannot tell whether their click
 * landed must not be able to pay twice for finding out.
 */
export function RunForm({ studioKey, inputSchema, estimatedCredits, isSubmitting, onSubmit }: Readonly<RunFormProps>) {
  const { t, locale } = useTranslation();
  const fields = useInputSchema(inputSchema, locale);
  const [values, setValues] = useState<Record<string, string>>({});
  const [uploading, setUploading] = useState<Record<string, boolean>>({});
  const setBusy = useCallback((key: string, busy: boolean) => {
    setUploading((current) =>
      current[key] === busy || (!busy && !current[key]) ? current : { ...current, [key]: busy },
    );
  }, []);
  const isUploading = Object.values(uploading).some(Boolean);

  /** The aspects the model in view can make, by the field they govern (a model field that declares them: x-options.aspects). */
  const aspectRule = (() => {
    for (const field of fields) {
      if (!field.aspectsByModel) continue;
      const model = (values[field.key] ?? "") || (field.defaultValue ?? "");
      const supported = field.aspectsByModel.aspects[model];
      return supported && supported.length > 0 ? { field: field.aspectsByModel.field, supported } : null;
    }
    return null;
  })();

  /** What a field holds: the person's answer, else — for a plain list — the default it shows selected. */
  const valueOf = (field: InputField) => {
    const own = values[field.key] ?? (field.type === "enum" ? (field.defaultValue ?? "") : "");
    // A model that cannot make the aspect in view moves the field to the first one it can (the hosted Wan: 16:9).
    if (aspectRule && field.key === aspectRule.field && own !== "" && !aspectRule.supported.includes(own)) {
      return aspectRule.supported[0];
    }
    return own;
  };

  const submit = () => {
    const payload: Record<string, unknown> = {};
    for (const field of fields) {
      const raw = valueOf(field).trim();
      if (raw === "") {
        continue;
      }
      payload[field.key] =
        field.type === "array"
          ? raw
              .split(",")
              .map((item) => item.trim())
              .filter(Boolean)
          : raw;
    }
    onSubmit(payload);
  };

  const missingRequired = fields.some((field) => field.required && !valueOf(field).trim());
  const main = fields.filter((field) => !field.advanced);
  const more = fields.filter((field) => field.advanced);
  const renderField = (field: InputField) => (
    <RunInput
      key={field.key}
      field={localized(field, t.studio.inputs, studioKey)}
      value={valueOf(field)}
      disabled={isSubmitting}
      allowedOptions={aspectRule && field.key === aspectRule.field ? aspectRule.supported : null}
      onBusyChange={(busy) => setBusy(field.key, busy)}
      onChange={(value) => setValues((current) => ({ ...current, [field.key]: value }))}
    />
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3">
        {main.map(renderField)}
        {more.length > 0 && (
          <details className="group rounded-lg border border-line px-3 py-2">
            <summary className="cursor-pointer select-none text-sm font-medium text-fg-muted">
              {t.studio.form.moreOptions}
            </summary>
            <div className="mt-3 flex flex-col gap-3">{more.map(renderField)}</div>
          </details>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-xs text-fg-muted">
          {t.studio.estimatedCost}: <span className="font-semibold text-fg">{estimatedCredits}</span>{" "}
          {t.studio.creditsPerRun}
        </span>
        <Button
          variant="primary"
          size="md"
          onClick={submit}
          disabled={missingRequired || isUploading}
          loading={isSubmitting}
        >
          {t.studio.runNow}
        </Button>
      </div>
    </div>
  );
}
