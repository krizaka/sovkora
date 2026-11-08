import React from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Txt, useTheme } from "@krizaka/ui/native";
import { ChevronRightIcon } from "@krizaka/icons/native";

interface Props {
  /** An @krizaka/icons glyph, drawn at 22 in the text colour you give it. */
  readonly icon: (color: string) => React.ReactNode;
  readonly label: string;
  /** One line under the label. */
  readonly hint?: string;
  /** The current value, on the right (a language, a theme). */
  readonly value?: string;
  readonly onPress?: () => void;
}

/**
 * A row of a settings list: icon, label, hint, value, chevron — on the kit's theme roles and `Txt`. (The kit has no
 * native list cell yet: this is the one composition the app owns, tracked for promotion into @krizaka/ui/native.)
 */
export function SettingsRow({ icon, label, hint, value, onPress }: Readonly<Props>): React.JSX.Element {
  const { theme } = useTheme();
  return (
    <Pressable
      role="button"
      accessibilityLabel={[label, value, hint].filter(Boolean).join(", ")}
      onPress={onPress}
      style={({ pressed }) => [styles.row, { backgroundColor: pressed ? theme.surface2 : "transparent" }]}
    >
      <View style={[styles.icon, { backgroundColor: theme.accentSoft }]}>{icon(theme.accentText)}</View>
      <View style={styles.text}>
        <Txt variant="body" numberOfLines={1}>
          {label}
        </Txt>
        {hint ? (
          <Txt variant="caption" tone="secondary" numberOfLines={2}>
            {hint}
          </Txt>
        ) : null}
      </View>
      {value ? (
        <Txt variant="caption" tone="secondary" numberOfLines={1} style={styles.value}>
          {value}
        </Txt>
      ) : null}
      {onPress ? <ChevronRightIcon size={18} color={theme.textMuted} /> : null}
    </Pressable>
  );
}

/** A titled group of rows on a card surface, hairline-separated. */
/** `Children.toArray` already keys every element; a bare string or number falls back to its text. */
function rowKey(row: ReturnType<typeof React.Children.toArray>[number], index: number): string {
  return React.isValidElement(row) && row.key !== null ? row.key : `row-${String(row)}-${String(index)}`;
}

export function SettingsGroup({
  title,
  children,
}: Readonly<{ title: string; children: React.ReactNode }>): React.JSX.Element {
  const { theme, radius } = useTheme();
  const rows = React.Children.toArray(children);
  return (
    <View style={styles.group}>
      <Txt variant="caption" tone="muted" style={styles.title}>
        {title}
      </Txt>
      <View
        style={[
          styles.card,
          { borderRadius: radius.lg, backgroundColor: theme.surface1, borderColor: theme.borderSubtle },
        ]}
      >
        {rows.map((row, index) => (
          <View
            key={rowKey(row, index)}
            style={index > 0 ? [styles.separator, { borderTopColor: theme.borderSubtle }] : undefined}
          >
            {row}
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    minHeight: 56,
  },
  icon: { width: 34, height: 34, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  text: { flex: 1, gap: 2 },
  value: { maxWidth: 140 },
  group: { gap: 8 },
  title: { textTransform: "uppercase", letterSpacing: 0.8, paddingHorizontal: 4 },
  card: { borderWidth: StyleSheet.hairlineWidth, overflow: "hidden" },
  separator: { borderTopWidth: StyleSheet.hairlineWidth },
});
