import React from "react";
import { StyleSheet, View } from "react-native";
import { TypingIndicator, useTheme } from "@krizaka/ui/native";
import { Markdown } from "../Markdown";
import { PipelineChips } from "./PipelineChips";
import { useT } from "../../i18n";
import type { ChatMessage } from "./types";

/**
 * An answer of the assistant: the kit's message-bubble shape (theirs, on the left, `surface2`) holding the answer as
 * Markdown — `MessageBubble` carries plain text only — with the real pipeline under it. While nothing has arrived, the
 * kit's typing indicator.
 */
export function AssistantMessage({ message }: Readonly<{ message: ChatMessage }>): React.JSX.Element {
  const { theme, radius } = useTheme();
  const t = useT();
  const waiting = message.content === "" && !message.settled;
  return (
    <View style={styles.row} testID={message.settled ? "answer-complete" : "answer-streaming"}>
      {waiting && <TypingIndicator label={t("chat.thinking")} />}
      {!waiting && message.content !== "" && (
        <View
          style={[styles.bubble, { borderRadius: radius.xl, borderTopLeftRadius: 6, backgroundColor: theme.surface2 }]}
        >
          <Markdown text={message.content} />
        </View>
      )}
      <PipelineChips steps={message.steps} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { alignSelf: "flex-start", maxWidth: "92%", gap: 8 },
  bubble: { paddingHorizontal: 14, paddingVertical: 10 },
});
