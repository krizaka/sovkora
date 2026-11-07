/**
 * @file useThreadManagement.ts
 * @description Hook managing conversation thread listings and creation.
 * Sourced from the backend via TanStack Query.
 */

import { useQueryClient, useQuery, useMutation } from "@tanstack/react-query";
import { parseISO } from "date-fns";
import { ChatThread } from "@/core/types/chat.types";
import { useAuth } from "@/core/hooks/useAuth";

/**
 * Retrieves cached conversation threads from local storage.
 * Returns an empty array if the store is empty — no default seeding.
 *
 * @param {string} userId - The active authenticated user ID.
 * @returns {ChatThread[]} The list of stored conversation threads.
 */
export const getStoredThreads = (userId: string): ChatThread[] => {
  if (globalThis.window === undefined) return [];
  const key = `orazaka_threads_${userId}`;
  const stored = localStorage.getItem(key);
  if (!stored) {
    return [];
  }
  try {
    return JSON.parse(stored);
  } catch (e) {
    console.error("Failed to parse stored threads:", e);
    return [];
  }
};

/**
 * Persists the current list of conversation threads in local storage.
 *
 * @param {string} userId - The active authenticated user ID.
 * @param {ChatThread[]} threads - The list of threads to serialize and save.
 */
export const saveStoredThreads = (userId: string, threads: ChatThread[]): void => {
  if (globalThis.window === undefined) return;
  localStorage.setItem(`orazaka_threads_${userId}`, JSON.stringify(threads));
};

/**
 * Hook providing thread listing, creation, and cache management.
 * Thread data originates exclusively from the database via the BFF.
 */
export function useThreadManagement() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id || user?.email || "anonymous";
  const queryKey = ["chatThreads", userId] as const;

  const threadsQuery = useQuery({
    queryKey,
    queryFn: async (): Promise<ChatThread[]> => {
      try {
        const res = await fetch("/api/v1/chat/sessions");
        if (!res.ok) {
          return getStoredThreads(userId);
        }
        const data = await res.json();
        const backendThreads = data.map((session: { id: string; title: string; updatedAt: string | number }) => ({
          conversationId: session.id,
          title: session.title,
          updatedAt: parseISO(session.updatedAt as string).getTime(),
        }));
        saveStoredThreads(userId, backendThreads);
        return backendThreads;
      } catch (e) {
        console.error("Failed to fetch threads:", e);
        return getStoredThreads(userId);
      }
    },
  });

  const createThreadMutation = useMutation({
    mutationFn: async (title: string): Promise<ChatThread> => {
      const newId =
        typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
          ? crypto.randomUUID()
          : `thread-${Date.now()}-${Array.from(crypto.getRandomValues(new Uint8Array(5)), (b) => b.toString(36)).join("")}`;

      const res = await fetch("/api/v1/chat/sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          conversationId: newId,
          title,
        }),
      });
      if (!res.ok) throw new Error("Failed to create thread");
      const session = await res.json();
      return {
        conversationId: session.id,
        title: session.title,
        updatedAt: parseISO(session.updatedAt as string).getTime(),
      };
    },
    onSuccess: (newThread) => {
      const cached = queryClient.getQueryData<ChatThread[]>(queryKey) || getStoredThreads(userId);
      const updatedThreads = [newThread, ...cached];
      saveStoredThreads(userId, updatedThreads);
      queryClient.setQueryData<ChatThread[]>(queryKey, updatedThreads);
      queryClient.invalidateQueries({ queryKey });
    },
  });

  const renameThreadMutation = useMutation({
    mutationFn: async ({ id, title }: { id: string; title: string }): Promise<ChatThread> => {
      const res = await fetch(`/api/v1/chat/sessions/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title }),
      });
      if (!res.ok) throw new Error("Failed to rename thread");
      const session = await res.json();
      return {
        conversationId: session.id,
        title: session.title,
        updatedAt: parseISO(session.updatedAt as string).getTime(),
      };
    },
    onSuccess: (updatedThread) => {
      const cached = queryClient.getQueryData<ChatThread[]>(queryKey) || getStoredThreads(userId);
      const updatedThreads = cached.map((t) => (t.conversationId === updatedThread.conversationId ? updatedThread : t));
      saveStoredThreads(userId, updatedThreads);
      queryClient.setQueryData<ChatThread[]>(queryKey, updatedThreads);
      queryClient.invalidateQueries({ queryKey });
    },
  });

  const deleteThreadMutation = useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const res = await fetch(`/api/v1/chat/sessions/${id}`, {
        method: "DELETE",
      });
      if (!res.ok) throw new Error("Failed to delete thread");
    },
    onSuccess: (_, id) => {
      const cached = queryClient.getQueryData<ChatThread[]>(queryKey) || getStoredThreads(userId);
      const updatedThreads = cached.filter((t) => t.conversationId !== id);
      saveStoredThreads(userId, updatedThreads);
      localStorage.removeItem(`orazaka_messages_${userId}_${id}`);
      queryClient.setQueryData<ChatThread[]>(queryKey, updatedThreads);
      queryClient.invalidateQueries({ queryKey });
    },
  });

  return {
    threads: threadsQuery.data ?? [],
    isLoadingThreads: threadsQuery.isLoading,
    createThread: async (title = "New Memory Block"): Promise<ChatThread> => {
      return createThreadMutation.mutateAsync(title);
    },
    renameThread: async (id: string, title: string): Promise<ChatThread> => {
      return renameThreadMutation.mutateAsync({ id, title });
    },
    deleteThread: async (id: string): Promise<void> => {
      return deleteThreadMutation.mutateAsync(id);
    },
  };
}
