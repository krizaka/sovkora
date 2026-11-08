/**
 * @file useMessageHistory.ts
 * @description Hook managing per-thread message history.
 * Message data is sourced exclusively from the backend via TanStack Query.
 * Local storage acts as a write-back cache for SSE-streamed tokens only.
 */

import { useQuery } from "@tanstack/react-query";
import { ChatMessage } from "@/core/types/chat.types";
import { useAuth } from "@/core/hooks/useAuth";

const buildKey = (userId: string, conversationId: string): readonly string[] =>
  ["chatMessages", userId, conversationId] as const;

/**
 * Retrieves cached messages for a given conversation from local storage.
 * Returns an empty array if no messages exist — no default seeding.
 *
 * @param {string} userId - The active authenticated user ID.
 * @param {string} conversationId - The target conversation thread ID.
 * @returns {ChatMessage[]} The list of stored messages for the conversation.
 */
export const getStoredMessages = (userId: string, conversationId: string): ChatMessage[] => {
  if (!conversationId || globalThis.window === undefined) return [];
  const stored = localStorage.getItem(`orazaka_messages_${userId}_${conversationId}`);
  if (!stored) return [];
  try {
    const parsed: Partial<ChatMessage>[] = JSON.parse(stored);
    return parsed.map((msg): ChatMessage => {
      const base = {
        id: msg.id ?? `msg-${Date.now()}`,
        role: msg.role ?? "assistant",
        content: msg.content ?? "",
        timestamp: msg.timestamp ?? Date.now(),
        ...(msg.attachment ? { attachment: msg.attachment } : {}),
      };
      // A persisted pending run keeps its runId so it can still resolve; without
      // one it cannot link or reconcile, so fall back to plain text. A placeholder
      // written before ADR-068 carried a jobId and lands here without a runId, which
      // is the same case: the bubble becomes the text it already held.
      if (msg.kind === "run-pending") {
        const pending = msg as Partial<ChatMessage> & { runId?: string };
        return pending.runId ? { ...base, kind: "run-pending", runId: pending.runId } : { ...base, kind: "text" };
      }
      return { ...base, kind: msg.kind ?? "text" };
    });
  } catch (e) {
    console.error(`Failed to parse messages for ${conversationId}:`, e);
    return [];
  }
};

/**
 * Persists messages for a given conversation in local storage.
 *
 * @param {string} userId - The active authenticated user ID.
 * @param {string} conversationId - The target conversation thread ID.
 * @param {ChatMessage[]} messages - The messages to serialize and save.
 */
export const saveStoredMessages = (userId: string, conversationId: string, messages: ChatMessage[]): void => {
  if (!conversationId || globalThis.window === undefined) return;
  localStorage.setItem(`orazaka_messages_${userId}_${conversationId}`, JSON.stringify(messages));
};

/**
 * Hook providing message history state for the active conversation thread.
 * Message data originates exclusively from the database via the BFF.
 *
 * @param {string} conversationId - The active target conversation thread ID.
 * @returns An object containing the messages list, loading state, and query key.
 */
export function useMessageHistory(conversationId: string) {
  const { user } = useAuth();
  const userId = user?.id || user?.email || "anonymous";
  const queryKey = buildKey(userId, conversationId);

  const messagesQuery = useQuery({
    queryKey,
    queryFn: () => getStoredMessages(userId, conversationId),
    enabled: !!conversationId,
  });

  return {
    messages: messagesQuery.data ?? [],
    isLoadingMessages: messagesQuery.isLoading,
    queryKey,
  };
}
