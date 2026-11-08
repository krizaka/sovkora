import React, { useState } from "react";
import { View } from "react-native";
import { Switch, TextField, Txt } from "@krizaka/ui/native";
import { useI18n } from "../i18n";

interface Props {
  readonly title: string;
  readonly value: string;
  readonly disabled: boolean;
  readonly onChange: (value: string) => void;
}

/** The seed: random by default (nothing is sent); turn the toggle off to type the number that repeats a result. */
export function SeedField({ title, value, disabled, onChange }: Props): React.JSX.Element {
  const { t } = useI18n();
  const [fixed, setFixed] = useState(value !== "");
  return (
    <View style={{ gap: 8 }}>
      <Txt variant="label" tone="secondary">
        {title}
      </Txt>
      <Switch
        label={t("studios.form.seedRandom")}
        showLabel
        description={t("studios.form.seedRandomHint")}
        checked={!fixed}
        disabled={disabled}
        onCheckedChange={(random) => {
          setFixed(!random);
          if (random) onChange("");
        }}
      />
      {fixed && (
        <TextField
          label={title}
          placeholder={t("studios.form.seedPlaceholder")}
          keyboardType="number-pad"
          maxLength={10}
          disabled={disabled}
          value={value}
          onValueChange={(text) => onChange(text.replace(/\D/g, ""))}
        />
      )}
    </View>
  );
}
