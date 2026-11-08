import React from "react";
import { ScrollView, StyleSheet } from "react-native";
import { Badge } from "@krizaka/ui/native";
import { humanizeInterceptor } from "../../core/pipeline";
import { useT, type Key } from "../../i18n";

/**
 * The interceptors that really processed this answer, one chip each, in the order they finished — the router's own
 * `pipeline-step` events, never a canned list. A step the catalogue has no word for is spaced from its id.
 */
export function PipelineChips({ steps }: Readonly<{ steps: readonly string[] }>): React.JSX.Element | null {
  const t = useT();
  if (steps.length === 0) return null;
  const word = (id: string): string => {
    const key = `pipeline.${id}` as Key;
    const text = t(key);
    // The engine answers with the key itself for a missing entry: then the id is spaced instead.
    return text === key ? humanizeInterceptor(id) : text;
  };
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.row}
      accessibilityLabel={t("chat.steps", { count: steps.length })}
    >
      {steps.map((id) => (
        <Badge key={id} size="sm" tone="neutral">
          {word(id)}
        </Badge>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  row: { gap: 6, paddingRight: 8 },
});
