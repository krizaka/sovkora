"use client";

import { useRef, useCallback, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { ChatMessage } from "@/core/types/chat.types";
import { useAuth } from "@/core/hooks/useAuth";
import { parseRouting, type ChatRouting } from "@/core/utils/routing";
import { parseControl, type ControlStatus } from "@/core/utils/control";
import { isPipelineSchema, type ChatPipelineSchema } from "@/core/types/pipeline.types";

const ROLES_WITH_VERDICT: ReadonlySet<ChatMessage["role"]> = new Set(["assistant"]);

/**
 * Puts the engine's verdict on the answer being streamed. Only an assistant message carries one: when the
 * final event (no text) arrives, the answer is already there and the verdict lands on it.
 */
export function withControl(messages: ChatMessage[], control: ControlStatus): ChatMessage[] {
  const last = messages.at(-1);
  return last && ROLES_WITH_VERDICT.has(last.role) ? [...messages.slice(0, -1), { ...last, control }] : messages;
}

async function readChatStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  assistantMsgId: string,
  userId: string,
  convId: string,
  queryClient: ReturnType<typeof useQueryClient>,
  queryKey: readonly ["chatMessages", string, string],
  onAccumulated: (content: string) => void,
  onPipeline: (schema: ChatPipelineSchema) => void,
  onStage: (interceptorId: string) => void,
  onRouting: (routing: ChatRouting) => void,
) {
  const decoder = new TextDecoder();
  let buffer = "";
  let accumulatedContent = "";
  // Track the current SSE `event:` so we can route the `pipeline-ack` payload
  // (the real interceptor chain) separately from `message` content tokens.
  let currentEvent = "message";
  // The verdict the engine last reported for this answer (PENDING on each chunk, then the final one).
  let control: ControlStatus | null = null;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";

    for (const line of lines) {
      const cleaned = line.trim();
      if (!cleaned) {
        currentEvent = "message"; // blank line = end of one SSE event
        continue;
      }
      if (cleaned.startsWith("event:")) {
        currentEvent = cleaned.slice(6).trim();
        continue;
      }

      if (cleaned.startsWith("data:")) {
        const raw = cleaned.slice(5).trim();

        // Early-Ack: the real interceptor pipeline composition (not content).
        if (currentEvent === "pipeline-ack") {
          try {
            const schema: unknown = JSON.parse(raw);
            if (isPipelineSchema(schema)) onPipeline(schema);
          } catch {
            // ignore malformed schema payloads
          }
          continue;
        }

        // The engine says where the answer was computed (provider, local or not, credit estimate).
        if (currentEvent === "routing") {
          try {
            const routing = parseRouting(JSON.parse(raw));
            if (routing) onRouting(routing);
          } catch {
            // ignore malformed routing payloads
          }
          continue;
        }

        // A single interceptor just finished — live pipeline progress.
        if (currentEvent === "pipeline-step") {
          try {
            const step = JSON.parse(raw) as { interceptorId?: unknown };
            if (typeof step.interceptorId === "string") onStage(step.interceptorId);
          } catch {
            // ignore malformed step payloads
          }
          continue;
        }

        let chunk = raw;
        try {
          const parsed = JSON.parse(chunk);
          // Defensive: a schema may arrive without an explicit event line.
          if (isPipelineSchema(parsed)) {
            onPipeline(parsed);
            continue;
          }
          control = parseControl(parsed.metadata) ?? control;
          chunk = parsed.content ?? parsed.text ?? "";
        } catch {
          // Fallback to raw string
        }

        if (chunk) {
          accumulatedContent += chunk;
          onAccumulated(accumulatedContent);
          queryClient.setQueryData<ChatMessage[]>(queryKey, (old = []) => {
            const lastMsg = old.at(-1);
            const updated: ChatMessage[] =
              lastMsg?.role === "assistant"
                ? [
                    ...old.slice(0, -1),
                    {
                      ...lastMsg,
                      content: accumulatedContent,
                      kind: "text" as const,
                      ...(control ? { control } : {}),
                    },
                  ]
                : [
                    ...old,
                    {
                      id: assistantMsgId,
                      role: "assistant",
                      content: accumulatedContent,
                      timestamp: Date.now(),
                      kind: "text" as const,
                      ...(control ? { control } : {}),
                    },
                  ];
            if (globalThis.window !== undefined) {
              localStorage.setItem(`orazaka_messages_${userId}_${convId}`, JSON.stringify(updated));
            }
            return updated;
          });
        } else if (control) {
          // The final event repeats no text: it only carries the verdict.
          const verdict = control;
          queryClient.setQueryData<ChatMessage[]>(queryKey, (old = []) => {
            const updated = withControl(old, verdict);
            if (updated !== old && globalThis.window !== undefined) {
              localStorage.setItem(`orazaka_messages_${userId}_${convId}`, JSON.stringify(updated));
            }
            return updated;
          });
        }
      }
    }
  }
}

function handleStreamError(
  err: unknown,
  prompt: string,
  convId: string,
  userId: string,
  accumulatedContent: string,
  queryClient: ReturnType<typeof useQueryClient>,
  queryKey: readonly ["chatMessages", string, string],
) {
  if (err instanceof Error && err.name === "AbortError") {
    return;
  }
  console.error("SSE stream error:", err);

  if (!accumulatedContent) {
    queryClient.setQueryData<ChatMessage[]>(queryKey, (old = []) => {
      const errorMsg = {
        id: `error-${Date.now()}`,
        role: "assistant" as const,
        content:
          "⚠️ Connection to the AI model failed. Make sure Ollama is running at the configured endpoint and try again.",
        timestamp: Date.now(),
        kind: "text" as const,
      };
      const updated = [...old, errorMsg];
      if (globalThis.window !== undefined) {
        localStorage.setItem(`orazaka_messages_${userId}_${convId}`, JSON.stringify(updated));
      }
      return updated;
    });
  }

  const title = prompt.length > 40 ? `${prompt.substring(0, 40)}...` : prompt;
  if (globalThis.window !== undefined) {
    const storedThreadsStr = localStorage.getItem("orazaka_threads");
    if (storedThreadsStr) {
      try {
        const threads = JSON.parse(storedThreadsStr) as Record<string, unknown>[];
        const updated = threads.map((t: Record<string, unknown>) =>
          t.conversationId === convId ? { ...t, title, updatedAt: Date.now() } : t,
        );
        localStorage.setItem("orazaka_threads", JSON.stringify(updated));
      } catch (e) {
        console.error(e);
      }
    }
  }

  queryClient.invalidateQueries({ queryKey: ["chatThreads"] });
  queryClient.invalidateQueries({ queryKey: ["operationGraph"] });
}

/**
 * Encapsulates the SSE chat streaming lifecycle:
 * - AbortController management
 * - Incremental token accumulation into React Query cache
 * - Error handling with localStorage fallback persistence
 */
export function useChatStreamClient() {
  const [isChatStreaming, setIsChatStreaming] = useState(false);
  const [chatPipeline, setChatPipeline] = useState<ChatPipelineSchema | null>(null);
  // Interceptor ids that have finished, in completion order (live progress).
  const [chatStageProgress, setChatStageProgress] = useState<string[]>([]);
  // Where the last answer ran — kept until the next one says otherwise, so the status line never blinks.
  const [chatRouting, setChatRouting] = useState<ChatRouting | null>(null);
  const chatAbortControllerRef = useRef<AbortController | null>(null);
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id || user?.email || "anonymous";

  const stopChatStream = useCallback(() => {
    if (chatAbortControllerRef.current) {
      chatAbortControllerRef.current.abort();
      chatAbortControllerRef.current = null;
    }
    setIsChatStreaming(false);
  }, []);

  const startChatStream = useCallback(
    async (convId: string, prompt: string, assetIds: string[] = []) => {
      stopChatStream();
      setIsChatStreaming(true);
      setChatPipeline(null);
      setChatStageProgress([]);

      const controller = new AbortController();
      chatAbortControllerRef.current = controller;

      const assistantMsgId = `assistant-${Date.now()}`;
      let accumulated = "";
      const queryKey = ["chatMessages", userId, convId] as const;

      try {
        const response = await fetch(`/api/chat/stream/${convId}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ prompt, assetIds }),
          signal: controller.signal,
        });

        if (!response.ok) {
          throw new Error(`Gateway stream error: ${response.statusText}`);
        }

        if (!response.body) {
          throw new Error("Response body is empty");
        }

        const reader = response.body.getReader();
        await readChatStream(
          reader,
          assistantMsgId,
          userId,
          convId,
          queryClient,
          queryKey,
          (content) => {
            accumulated = content;
          },
          setChatPipeline,
          (interceptorId) =>
            setChatStageProgress((prev) => (prev.includes(interceptorId) ? prev : [...prev, interceptorId])),
          setChatRouting,
        );
      } catch (err) {
        handleStreamError(err, prompt, convId, userId, accumulated, queryClient, queryKey);
      } finally {
        setIsChatStreaming(false);
        if (chatAbortControllerRef.current === controller) {
          chatAbortControllerRef.current = null;
        }
      }
    },
    [stopChatStream, queryClient, userId],
  );

  return {
    isChatStreaming,
    chatPipeline,
    chatStageProgress,
    chatRouting,
    startChatStream,
    stopChatStream,
  };
}
