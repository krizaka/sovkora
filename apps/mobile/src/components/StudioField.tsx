import React from "react";
import { StyleSheet, View } from "react-native";
import { Chip, TextField, Txt } from "@krizaka/ui/native";
import { ChoiceField } from "./ChoiceField";
import { DocumentField } from "./DocumentField";
import { FileFieldBox } from "./FileFieldBox";
import { PhotoField } from "./PhotoField";
import { SeedField } from "./SeedField";
import type { SchemaField } from "../core/inputSchema";
import { useI18n } from "../i18n";

type FieldProps = Readonly<{
  field: SchemaField;
  title: string;
  choiceLabel: (key: string, value: string) => string;
  value: string;
  disabled: boolean;
  token: string | null;
  onAnswer: (value: string) => void;
  onBusy: (busy: boolean) => void;
}>;

/** The input a Studio field asks for: a PDF, a photo, a choice, a seed, a few options or plain text. */
export function StudioField({
  field,
  title,
  choiceLabel,
  value,
  disabled,
  token,
  onAnswer,
  onBusy,
}: FieldProps): React.JSX.Element {
  const { t } = useI18n();
  if (field.documentKind === "pdf") {
    return (
      <FileFieldBox field={field} title={title}>
        <DocumentField title={title} maxLength={field.maxLength} onValueChange={onAnswer} onBusyChange={onBusy} />
      </FileFieldBox>
    );
  }
  if (field.assetKind === "image") {
    return (
      <FileFieldBox field={field} title={title}>
        <PhotoField title={title} token={token} onAssetChange={onAnswer} onBusyChange={onBusy} />
      </FileFieldBox>
    );
  }
  if (field.choices !== undefined || field.fedByCatalogue === true) {
    return <ChoiceField field={field} title={title} value={value} disabled={disabled} onChange={onAnswer} />;
  }
  if (field.control === "seed") {
    return <SeedField title={title} value={value} disabled={disabled} onChange={onAnswer} />;
  }
  if (field.options) {
    return (
      <View style={styles.field}>
        <Txt variant="label" tone="secondary">
          {title}
        </Txt>
        <Chip.Group type="single" aria-label={title} value={value || (field.options[0] ?? "")} onValueChange={onAnswer}>
          {field.options.map((option) => (
            <Chip key={option} value={option}>
              {choiceLabel(field.key, option)}
            </Chip>
          ))}
        </Chip.Group>
      </View>
    );
  }
  const long = field.key === "prompt" || field.key === "text";
  return (
    <TextField
      testID={`field-${field.key}`}
      label={`${title}${field.required ? " *" : ""}`}
      placeholder={field.isList ? t("studios.listPlaceholder") : undefined}
      multiline={field.isList || long}
      keyboardType={field.isNumber ? "number-pad" : "default"}
      maxLength={field.maxLength}
      disabled={disabled}
      value={value}
      onValueChange={onAnswer}
      returnKeyType={long ? "done" : undefined}
      blurOnSubmit
    />
  );
}

const styles = StyleSheet.create({
  field: { gap: 8 },
});
