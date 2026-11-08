"use client";

import { useEffect } from "react";
import { Badge } from "@krizaka/ui/badge";
import { Combobox, type ComboboxOption } from "@krizaka/ui/combobox";
import { useTranslation } from "@/core/context/LocaleContext";
import { choiceFacts, unavailableWords } from "@/features/studio/components/describeChoice";
import type { Choice, InputField } from "@/features/studio/hooks/useInputSchema";

interface ChoicePickerProps {
  field: InputField;
  choices: NonNullable<InputField["choices"]>;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}

/**
 * A closed list the catalogue fed, as a searchable list: each entry is a short name and one line of what it is for;
 * one whose engine is off is shown but cannot be picked, with the reason. The facts of the model in view — where it
 * runs, how fast, about how long, what it costs, the size it makes — are chips under the field, for that model only;
 * the whole list side by side is one click away (« Compare models »). Left alone, the field stays on « Automatic »
 * and sends nothing, so the platform keeps choosing the best available model at run time.
 */
export function ChoicePicker({ field, choices, value, disabled, onChange }: Readonly<ChoicePickerProps>) {
  const { t } = useTranslation();
  const form = t.studio.form;
  const options: ComboboxOption[] = choices.map((choice) => ({
    value: choice.value,
    label: choice.label,
    description:
      choice.meta?.available === false
        ? unavailableWords(choice, form.unavailableCodes, form.unavailable)
        : (choice.hint ?? undefined),
    disabled: choice.meta?.available === false,
  }));
  const chosen = options.find((option) => option.value === value) ?? null;
  const running = choices.find((choice) => choice.value === field.defaultValue);
  const shown = choices.find((choice) => choice.value === (chosen?.value ?? field.defaultValue));
  return (
    <div className="flex flex-col gap-2" data-testid={`choice-picker-${field.key}`}>
      <Combobox
        label={field.title}
        options={options}
        value={chosen}
        onValueChange={(option) => onChange(option?.value ?? "")}
        placeholder={running ? form.automatic.replace("{name}", running.label) : form.automaticPlain}
        hint={field.description ?? shown?.hint ?? form.automaticHint}
        disabled={disabled}
        labels={{
          empty: form.noResults,
          clear: form.clear,
          toggle: form.showOptions,
        }}
      />
      {shown && <ChoiceChips choice={shown} />}
      {choices.length > 1 && <CompareChoices choices={choices} />}
    </div>
  );
}

/** The facts of one model as chips: where it runs, how fast, how long, what it costs, what size it makes. */
function ChoiceChips({ choice, compare = false }: Readonly<{ choice: Choice; compare?: boolean }>) {
  const { t } = useTranslation();
  const facts = choiceFacts(choice, t.studio.form);
  if (facts.length === 0) {
    return null;
  }
  return (
    <ul
      className="flex flex-wrap gap-1.5"
      aria-label={compare ? choice.label : `${t.studio.form.chosenLabel}: ${choice.label}`}
    >
      {facts.map((fact) => (
        <li key={fact}>
          <Badge tone="neutral" size="sm">
            {fact}
          </Badge>
        </li>
      ))}
    </ul>
  );
}

/** Every choice side by side, from the catalogue's facts — the long text of the helper, as data a person can scan. */
function CompareChoices({ choices }: Readonly<{ choices: Choice[] }>) {
  const { t } = useTranslation();
  return (
    <details className="rounded-lg border border-line px-3 py-2">
      <summary className="cursor-pointer select-none text-sm font-medium text-fg-muted">
        {t.studio.form.compare}
      </summary>
      <p className="mt-2 text-xs text-fg-muted">{t.studio.form.compareHint}</p>
      <ul className="mt-3 flex flex-col gap-3">
        {choices.map((choice) => (
          <li key={choice.value} className="flex flex-col gap-1">
            <span className="text-sm font-medium text-fg">{choice.label}</span>
            {choice.hint && <span className="text-xs text-fg-muted">{choice.hint}</span>}
            <ChoiceChips choice={choice} compare />
          </li>
        ))}
      </ul>
    </details>
  );
}

/** A catalogue-fed or closed list with nothing to offer: said out loud (and logged), never a blank select. */
export function EmptyChoices({ field, message }: Readonly<{ field: InputField; message: string }>) {
  useEffect(() => {
    console.error(`Studio form: the « ${field.key} » list has no option to offer.`);
  }, [field.key]);
  return (
    <div className="flex flex-col gap-1.5" data-testid={`empty-choices-${field.key}`}>
      <span className="text-sm font-medium text-fg">{field.title}</span>
      <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-fg">
        {message}
      </p>
    </div>
  );
}
