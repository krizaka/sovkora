"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "@/core/context/LocaleContext";
import { saveStoredMessages } from "@/features/chat-session/hooks/useMessageHistory";
import { deriveThreadTitle } from "@/features/chat-session/utils/threadTitle";
import type { ChatMessage, ChatThread } from "@/core/types/chat.types";

interface ChatThreadActionsParams {
  activeConversationId: string;
  setActiveConversationId: (id: string) => void;
  setChatInput: (value: string) => void;
  startChatStream: (conversationId: string, prompt: string) => void;
  userId: string;
  threads: ChatThread[];
  createThread: (title?: string) => Promise<ChatThread>;
  renameThread: (id: string, title: string) => Promise<ChatThread>;
  deleteThread: (id: string) => Promise<void>;
}

/**
 * Encapsulates ChatWindow's thread lifecycle handlers (new / first-send / rename / delete) and the
 * derived active-thread title, keeping the component within the §8 250-line budget.
 */
export function useChatThreadActions({
  activeConversationId,
  setActiveConversationId,
  setChatInput,
  startChatStream,
  userId,
  threads,
  createThread,
  renameThread,
  deleteThread,
}: ChatThreadActionsParams) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { t } = useTranslation();

  /**
   * "New chat" — deferred creation. We do NOT persist a thread yet; we just drop the user into the
   * empty composer. The thread is created on the first message (handleFirstSend).
   */
  const handleNewChat = async () => {
    setChatInput("");
    setActiveConversationId("");
    router.push("/chat");
  };

  /**
   * First message of a brand-new conversation: create the thread (titled from the prompt), seed the
   * user message into its cache, navigate in, and open the SSE stream — or, when a Studio is staged,
   * run it in the new thread instead.
   */
  const handleFirstSend = async (prompt: string, runStagedIn?: (conversationId: string) => boolean) => {
    const trimmed = prompt.trim();
    if (!trimmed) return;
    try {
      const newThread = await createThread(deriveThreadTitle(trimmed));
      const newId = newThread.conversationId;
      // A Studio staged in the composer runs in the new thread (it echoes the request itself).
      if (runStagedIn?.(newId)) {
        setActiveConversationId(newId);
        setChatInput("");
        router.push(`/chat?conversationId=${newId}`);
        return;
      }
      const userMsg: ChatMessage = {
        id: `user-${Date.now()}`,
        role: "user",
        content: trimmed,
        timestamp: Date.now(),
        kind: "text",
      };
      queryClient.setQueryData<ChatMessage[]>(["chatMessages", userId, newId], [userMsg]);
      saveStoredMessages(userId, newId, [userMsg]);
      setActiveConversationId(newId);
      setChatInput("");
      router.push(`/chat?conversationId=${newId}`);
      startChatStream(newId, trimmed);
    } catch (e) {
      console.error("Failed to start new chat:", e);
    }
  };

  const handleRenameThread = async (id: string, title: string) => {
    try {
      await renameThread(id, title);
    } catch (e) {
      console.error("Failed to rename thread:", e);
    }
  };

  const handleDeleteThread = async (id: string) => {
    if (globalThis.window !== undefined && !globalThis.confirm(t.chat.deleteConfirm)) {
      return;
    }
    try {
      await deleteThread(id);
      if (activeConversationId === id) {
        setActiveConversationId("");
        router.push("/chat");
      }
    } catch (e) {
      console.error("Failed to delete thread:", e);
    }
  };

  const activeThread = threads.find((th) => th.conversationId === activeConversationId);
  const threadTitle = activeThread ? activeThread.title : "";

  return {
    handleNewChat,
    handleFirstSend,
    handleRenameThread,
    handleDeleteThread,
    threadTitle,
  };
}
