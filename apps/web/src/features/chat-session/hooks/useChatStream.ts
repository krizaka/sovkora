/**
 * @file useChatStream.ts
 * @description Hook orchestrating chat via REST SSE token streaming (ADR-028: chat is REST SSE, not
 * GraphQL). Delegates actual streaming state tracking to JobStreamContext.
 */

import { useJobStream } from "@/core/context/JobStreamContext";
import { useThreadManagement } from "@/features/chat-session/hooks/useThreadManagement";
import { useMessageHistory, saveStoredMessages } from "@/features/chat-session/hooks/useMessageHistory";
import { useQueryClient } from "@tanstack/react-query";
import type { ChatMessage } from "@/core/types/chat.types";
import { useAuth } from "@/core/hooks/useAuth";

// ── Message Factories ────────────────────────────────────────────────────────

const createUserMessage = (prompt: string): ChatMessage => ({
  id: `user-${Date.now()}`,
  role: "user",
  content: prompt,
  timestamp: Date.now(),
  kind: "text",
});

// ── Hook ─────────────────────────────────────────────────────────────────────

/**
 * Orchestrating hook providing message mutation, SSE streaming, thread management, and message history.
 *
 * @param conversationId - The active target conversation thread ID.
 * @returns An object containing threads, messages, loading states, SSE streaming toggle, and mutator handlers.
 */
export function useChatStream(conversationId: string) {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id || user?.email || "anonymous";

  const { threads, isLoadingThreads, createThread, renameThread, deleteThread } = useThreadManagement();
  const { messages, isLoadingMessages, queryKey } = useMessageHistory(conversationId);

  const { isChatStreaming, startChatStream } = useJobStream();

  const sendMessage = async (prompt: string) => {
    // Append the user's prompt to UI state instantly upon submission before the SSE connection opens
    const userMsg = createUserMessage(prompt);
    queryClient.setQueryData<ChatMessage[]>(queryKey, (old = []) => {
      const updated = [...old, userMsg];
      saveStoredMessages(userId, conversationId, updated);
      return updated;
    });

    startChatStream(conversationId, prompt);
  };

  return {
    threads,
    isLoadingThreads,
    messages,
    isLoadingMessages,
    sendMessage,
    isSending: isChatStreaming,
    isStreaming: isChatStreaming,
    isGenerating: isChatStreaming,
    error: null,
    createThread,
    renameThread,
    deleteThread,
  };
}
