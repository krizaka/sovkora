import React from "react";
import { StyleSheet, View } from "react-native";
import { Txt } from "@krizaka/ui/native";
import type { SchemaField } from "../core/inputSchema";

interface Props {
  readonly field: SchemaField;
  readonly title: string;
  readonly children: React.ReactNode;
}

/** The frame of a Studio input that takes a file (a photo, a PDF): its title, a star when required, its picker below. */
export function FileFieldBox({ field, title, children }: Props): React.JSX.Element {
  return (
    <View style={styles.field} testID={`field-${field.key}`}>
      <Txt variant="label" tone="secondary">
        {`${title}${field.required ? " *" : ""}`}
      </Txt>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({ field: { gap: 8 } });
