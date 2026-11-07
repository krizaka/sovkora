import React from "react";
import { Linking, StyleSheet, Text, View } from "react-native";
import { useTheme } from "@krizaka/ui/native";
import { parseMarkdown, type Block, type Inline } from "../core/markdown";

/** One line of inline marks, drawn as nested `Text` so it wraps like prose. */
function Spans({ inline }: Readonly<{ inline: readonly Inline[] }>): React.JSX.Element {
  const { theme } = useTheme();
  return (
    <>
      {inline.map((span, index) => {
        const key = `${span.kind}-${String(index)}`;
        switch (span.kind) {
          case "bold":
            return (
              <Text key={key} style={styles.bold}>
                {span.text}
              </Text>
            );
          case "italic":
            return (
              <Text key={key} style={styles.italic}>
                {span.text}
              </Text>
            );
          case "code":
            return (
              <Text key={key} style={[styles.code, { backgroundColor: theme.surface3 }]}>
                {span.text}
              </Text>
            );
          case "link":
            return (
              <Text
                key={key}
                accessibilityRole="link"
                style={{ color: theme.accentText, textDecorationLine: "underline" }}
                onPress={() => void Linking.openURL(span.href)}
              >
                {span.text}
              </Text>
            );
          default:
            return <Text key={key}>{span.text}</Text>;
        }
      })}
    </>
  );
}

const HEADING_SIZE = { 1: 20, 2: 17, 3: 15 } as const;

function BlockView({ block }: Readonly<{ block: Block }>): React.JSX.Element {
  const { theme } = useTheme();
  const body = { color: theme.textPrimary, fontSize: 15, lineHeight: 22 };
  switch (block.kind) {
    case "heading":
      return (
        <Text accessibilityRole="header" style={[body, styles.heading, { fontSize: HEADING_SIZE[block.level] }]}>
          <Spans inline={block.inline} />
        </Text>
      );
    case "list":
      return (
        <View style={styles.list}>
          {block.items.map((item, index) => (
            <View key={`item-${String(index)}`} style={styles.item}>
              <Text style={[body, { color: theme.accentText, width: 22 }]}>
                {block.ordered ? `${String(index + 1)}.` : "•"}
              </Text>
              <Text style={[body, styles.itemText]}>
                <Spans inline={item} />
              </Text>
            </View>
          ))}
        </View>
      );
    case "quote":
      return (
        <View style={[styles.quote, { borderLeftColor: theme.accent }]}>
          <Text style={[body, { color: theme.textSecondary, fontStyle: "italic" }]}>
            <Spans inline={block.inline} />
          </Text>
        </View>
      );
    case "code":
      return (
        <View style={[styles.fence, { backgroundColor: theme.surface3 }]}>
          <Text style={[styles.fenceText, { color: theme.textPrimary }]}>{block.text}</Text>
        </View>
      );
    case "rule":
      return <View style={[styles.rule, { backgroundColor: theme.borderDefault }]} />;
    default:
      return (
        <Text style={body}>
          <Spans inline={block.inline} />
        </Text>
      );
  }
}

/**
 * A model's answer, drawn natively: headings, lists, quotes, code, bold and links — never a literal `**`.
 * `text` may be a half-written, still streaming answer.
 */
export function Markdown({ text }: Readonly<{ text: string }>): React.JSX.Element {
  const blocks = React.useMemo(() => parseMarkdown(text), [text]);
  return (
    <View style={styles.root}>
      {blocks.map((block, index) => (
        <BlockView key={`${block.kind}-${String(index)}`} block={block} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { gap: 10 },
  bold: { fontWeight: "700" },
  italic: { fontStyle: "italic" },
  code: { fontFamily: "Menlo", fontSize: 13 },
  heading: { fontWeight: "800", marginTop: 4 },
  list: { gap: 4 },
  item: { flexDirection: "row" },
  itemText: { flex: 1 },
  quote: { borderLeftWidth: 3, paddingLeft: 12 },
  fence: { borderRadius: 10, padding: 12 },
  fenceText: { fontFamily: "Menlo", fontSize: 12.5, lineHeight: 18 },
  rule: { height: StyleSheet.hairlineWidth },
});
