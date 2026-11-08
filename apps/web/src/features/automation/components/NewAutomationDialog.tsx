"use client";

import * as React from "react";
import { z } from "zod";
import { Dialog } from "@krizaka/ui/dialog";
import { createKrizakaForm } from "@krizaka/ui/form";
import { useTranslation } from "@/core/context/LocaleContext";
import { useAutomationMutations, useRunnableStudios } from "@/features/automation/hooks/useAutomations";

/** « Empty » or a JSON object — what the API takes as fixed inputs. */
function parseInputs(text: string): Record<string, unknown> | null {
  if (text.trim() === "") return {};
  try {
    const value: unknown = JSON.parse(text);
    return value !== null && typeof value === "object" && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

const newAutomation = createKrizakaForm({
  schema: z.object({
    name: z.string().trim().min(1, "automations.name"),
    studioKey: z.string().min(1, "automations.studio"),
    inputs: z.string().refine((text) => parseInputs(text) !== null, "automations.inputs"),
  }),
  defaultValues: { name: "", studioKey: "", inputs: "" },
});

/** The create form, remounted at every opening so it starts clean. */
function NewAutomationForm({ onDone }: Readonly<{ onDone: () => void }>) {
  const { t } = useTranslation();
  const a = t.automations;
  const studios = useRunnableStudios();
  const { create } = useAutomationMutations();
  const words: Record<string, string> = {
    "automations.name": a.form.nameRequired,
    "automations.studio": a.form.studioRequired,
    "automations.inputs": a.form.inputsInvalid,
  };
  const form = newAutomation.useForm({
    i18n: { t: (key) => words[key] },
    onSubmit: async (values) => {
      await create.mutateAsync({
        name: values.name,
        studioKey: values.studioKey,
        inputs: parseInputs(values.inputs) ?? {},
      });
      onDone();
    },
  });
  const options = (studios.data ?? []).map((studio) => ({ value: studio.studioKey, label: studio.label }));
  return (
    <form.Form aria-label={a.form.title}>
      <form.TextField name="name" label={a.form.name} placeholder={a.form.namePlaceholder} autoComplete="off" />
      <form.Select name="studioKey" label={a.form.studio} placeholder={a.form.studioPlaceholder} options={options} />
      <form.TextArea name="inputs" label={a.form.inputs} hint={a.form.inputsHint} rows={4} />
      {create.isError && (
        <p role="alert" className="text-xs text-danger">
          {a.form.createError}
        </p>
      )}
      <form.FormError />
      <form.Submit>{a.form.submit}</form.Submit>
    </form.Form>
  );
}

/** The « new automation » dialog: a name, the Studio to run and its fixed answers. */
export function NewAutomationDialog({ open, onClose }: Readonly<{ open: boolean; onClose: () => void }>) {
  const { t } = useTranslation();
  const a = t.automations;
  return (
    <Dialog.Root open={open} onOpenChange={(next) => !next && onClose()}>
      <Dialog.Content closeLabel={a.form.cancel}>
        <Dialog.Header>
          <Dialog.Title>{a.form.title}</Dialog.Title>
          <Dialog.Description>{a.form.desc}</Dialog.Description>
        </Dialog.Header>
        <Dialog.Body>{open && <NewAutomationForm onDone={onClose} />}</Dialog.Body>
      </Dialog.Content>
    </Dialog.Root>
  );
}
