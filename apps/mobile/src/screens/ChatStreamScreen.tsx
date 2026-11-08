import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FlatList, Keyboard, StyleSheet, View } from "react-native";
import {
  ActionSheet,
  Card,
  EmptyState,
  Header,
  IconButton,
  MessageBubble,
  MessageComposer,
  OrazakaMark,
  Screen,
  Txt,
  useTheme,
} from "@krizaka/ui/native";
import { PlusIcon } from "@krizaka/icons/native";
import { useToken } from "../core/AuthContext";
import { streamChat, type StreamFailure } from "../core/streamChat";
import { useApi } from "../core/useApi";
import { answeringModel, chatModels, type CatalogModel } from "../core/sovereignty";
import { AssistantMessage } from "../components/chat/AssistantMessage";
import { ChatMeta } from "../components/chat/ChatMeta";
import type { ChatMessage } from "../components/chat/types";
import { useT, type Key } from "../i18n";

const SUGGESTIONS: readonly Key[] = ["chat.suggestions.contract", "chat.suggestions.reply", "chat.suggestions.law25"];

/** Nothing said yet: the kit's empty state, with three things to try (each one sends itself). */
function Welcome({ onPick }: Readonly<{ onPick: (prompt: string) => void }>): React.JSX.Element {
  const t = useT();
  return (
    <EmptyState
      icon={<OrazakaMark size={56} />}
      title={t("chat.emptyTitle")}
      description={t("chat.emptyDescription")}
      action={
        <View style={styles.suggestions} accessibilityLabel={t("chat.suggestionsLabel")}>
          {SUGGESTIONS.map((key) => (
            <Card.Root key={key} onPress={() => onPick(t(key))} accessibilityRole="button" accessibilityLabel={t(key)}>
              <Card.Body padding="sm">
                <Txt variant="label" style={styles.suggestion}>
                  {t(key)}
                </Txt>
              </Card.Body>
            </Card.Root>
          ))}
        </View>
      }
    />
  );
}

/** One real conversation: the kit's bubbles and composer, the answer streamed, the pipeline that really ran. */
export function ChatStreamScreen(): React.JSX.Element {
  const t = useT();
  const { theme } = useTheme();
  const bearer = useToken();
  const catalog = useApi<CatalogModel[]>("/api/v1/models/catalog");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [picked, setPicked] = useState<string | null>(null);
  const [choosing, setChoosing] = useState(false);
  // One conversation per "new chat", so a reply lands in the thread that asked for it.
  const conversationId = useRef(`mobile-${String(Date.now())}`);
  const abort = useRef<(() => void) | null>(null);
  const answered = useRef(false);
  const list = useRef<FlatList<ChatMessage>>(null);

  // Leaving the screen mid-answer must close the request, not leave it writing into a dead tree.
  useEffect(() => () => abort.current?.(), []);

  const models = useMemo(() => chatModels(catalog.data ?? []), [catalog.data]);
  const model = useMemo(() => answeringModel(catalog.data ?? [], picked), [catalog.data, picked]);

  const patchReply = useCallback((replyId: string, patch: (m: ChatMessage) => ChatMessage) => {
    setMessages((prev) => prev.map((m) => (m.id === replyId ? patch(m) : m)));
  }, []);

  const wordsOf = useCallback(
    (f: StreamFailure): string => {
      if (f.kind === "unauthorized") return t("common.sessionExpired");
      if (f.kind === "unreachable") return t("common.unreachable");
      return t("common.streamInterrupted", { status: f.status });
    },
    [t],
  );

  const send = useCallback(
    (text: string) => {
      const prompt = text.trim();
      if (prompt === "" || isSending) return;
      const stamp = String(Date.now());
      const replyId = `msg-${stamp}-reply`;
      setMessages((prev) => [
        ...prev,
        { id: `msg-${stamp}`, role: "user", content: prompt, planned: [], steps: [], settled: true },
        { id: replyId, role: "assistant", content: "", planned: [], steps: [], settled: false },
      ]);
      setInput("");
      // The answer streams in view: the keyboard steps aside (and the tab bar is reachable again).
      Keyboard.dismiss();
      setIsSending(true);
      setFailure(null);
      answered.current = false;
      abort.current = streamChat(
        conversationId.current,
        prompt,
        bearer,
        {
          onContent: (accumulated) => {
            answered.current = true;
            patchReply(replyId, (m) => ({ ...m, content: accumulated }));
          },
          onPipeline: (ids) => patchReply(replyId, (m) => ({ ...m, planned: ids })),
          onStep: (id) => patchReply(replyId, (m) => ({ ...m, steps: [...m.steps, id] })),
          onDone: () => {
            patchReply(replyId, (m) => ({ ...m, settled: true }));
            // A stream that ends without a word (the model is down) is said, not left as an empty bubble.
            if (!answered.current) setFailure(t("chat.noAnswer"));
            setIsSending(false);
          },
          onError: (f) => {
            patchReply(replyId, (m) => ({ ...m, settled: true }));
            setFailure(wordsOf(f));
            setIsSending(false);
          },
        },
        { model: picked },
      );
    },
    [bearer, isSending, patchReply, picked, t, wordsOf],
  );

  const newChat = () => {
    abort.current?.();
    conversationId.current = `mobile-${String(Date.now())}`;
    setMessages([]);
    setFailure(null);
    setIsSending(false);
  };

  return (
    <Screen
      scroll={false}
      header={
        <Header
          title={t("chat.title")}
          size="inline"
          actions={
            messages.length > 0 ? (
              <IconButton
                label={t("chat.newChat")}
                variant="ghost"
                size="sm"
                icon={<PlusIcon size={20} color={theme.textPrimary} />}
                onPress={newChat}
              />
            ) : undefined
          }
        />
      }
      footer={
        <View>
          {failure !== null && (
            <Txt variant="caption" tone="danger" style={styles.failure}>
              {failure}
            </Txt>
          )}
          <ChatMeta model={model} onPickModel={() => setChoosing(true)} />
          <MessageComposer
            value={input}
            onValueChange={setInput}
            onSend={() => send(input)}
            placeholder={t("chat.placeholder")}
            sendLabel={t("chat.send")}
            // The tab bar below already clears the home indicator: the composer does not add it twice.
            style={styles.composer}
          />
        </View>
      }
    >
      {messages.length === 0 ? (
        <Welcome onPick={send} />
      ) : (
        <FlatList
          ref={list}
          data={messages}
          keyExtractor={(m) => m.id}
          contentContainerStyle={styles.messages}
          onContentSizeChange={() => list.current?.scrollToEnd({ animated: true })}
          keyboardShouldPersistTaps="handled"
          renderItem={({ item }) =>
            item.role === "user" ? <MessageBubble text={item.content} mine /> : <AssistantMessage message={item} />
          }
        />
      )}
      <ActionSheet
        open={choosing}
        onOpenChange={setChoosing}
        title={t("chat.chooseModel")}
        cancelLabel={t("common.cancel")}
        actions={models.map((m) => ({
          label: `${m.modelLabel ?? m.modelName}${m.modelName === model?.modelName ? "  ✓" : ""}`,
          onPress: () => setPicked(m.isDefault === true ? null : m.modelName),
        }))}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  messages: { padding: 16, gap: 12, flexGrow: 1 },
  suggestions: { alignSelf: "stretch", gap: 8, paddingHorizontal: 8 },
  suggestion: { textAlign: "center" },
  failure: { textAlign: "center", paddingVertical: 6 },
  composer: { paddingBottom: 8 },
});
