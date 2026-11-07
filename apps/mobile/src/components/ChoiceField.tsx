import React from "react";
import { Select } from "@krizaka/ui/native";
import type { Choice, SchemaField } from "../core/inputSchema";
import { useI18n } from "../i18n";
import { describeChoice, type ChoiceWords } from "./describeChoice";

interface Props {
  readonly field: SchemaField;
  readonly title: string;
  readonly value: string;
  readonly disabled: boolean;
  readonly onChange: (value: string) => void;
}

/**
 * A closed list the catalogue fed, as the kit's bottom-sheet picker. Each entry says where it runs, how fast, about how
 * long and what it costs; one whose engine is off is listed but cannot be chosen, with the reason. Left alone it stays on
 * « Automatic » and sends nothing, so the platform keeps choosing the best available model at run time. A list that did
 * not load is still a picker (just « Automatic »), never a box to type a model id in.
 */
export function ChoiceField({ field, title, value, disabled, onChange }: Props): React.JSX.Element {
  const { t } = useI18n();
  const words: ChoiceWords = {
    local: t("studios.form.local"),
    cloud: t("studios.form.cloud"),
    fast: t("studios.form.fast"),
    balanced: t("studios.form.balanced"),
    slow: t("studios.form.slow"),
    about: (n) => t("studios.form.about", { n }),
    perRun: (price) => t("studios.form.perRun", { price }),
    free: t("studios.form.free"),
  };
  const choices: readonly Choice[] = field.choices ?? [];
  const running = choices.find((choice) => choice.value === field.defaultValue);
  const automatic =
    running === undefined ? t("studios.form.automaticPlain") : t("studios.form.automatic", { name: running.label });
  const shown = choices.find((choice) => choice.value === (value === "" ? field.defaultValue : value));
  return (
    <Select
      label={title}
      sheetTitle={title}
      placeholder={automatic}
      disabled={disabled}
      hint={
        shown?.hint ??
        (field.choices === undefined ? t("studios.form.choicesUnreachable") : t("studios.form.automaticHint"))
      }
      value={value}
      onValueChange={onChange}
      options={[
        { value: "", label: automatic },
        ...choices.map((choice) => ({
          value: choice.value,
          label: choice.label,
          description: describeChoice(choice, words),
          disabled: !choice.available,
        })),
      ]}
    />
  );
}
