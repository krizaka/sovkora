import type { ControlStatus } from "@/core/utils/control";

/**
 * User representation inside the chat session context.
 */
export interface User {
  id: string;
  username: string;
  preferences: Record<string, unknown>;
}

/**
 * Chat response wrapper returned by backend streaming or REST endpoints.
 */
export interface ChatResponse {
  content: string;
  conversationId: string;
  metadata?: Record<string, unknown>;
}

export interface BaseChatMessage {
  id: string;
  role: "user" | "assistant" | "system";
  timestamp: number;
  /**
   * Optional reference to a file the user attached to this message (e.g. an image
   * sent for analysis). Kept so the conversation preserves a visible trace of what
   * was exchanged. `url` is a small inline preview persisted with the history.
   */
  attachment?: { assetId: string; name: string; url?: string };
  /** Where the answer stands with the engine's control loop (assistant messages only). */
  control?: ControlStatus;
}

export interface TextChatMessage extends BaseChatMessage {
  kind: "text";
  content: string;
}

export interface ImageChatMessage extends BaseChatMessage {
  kind: "image";
  content: string;
}

export interface AudioChatMessage extends BaseChatMessage {
  kind: "audio";
  content: string;
}

export interface VideoChatMessage extends BaseChatMessage {
  kind: "video";
  content: string;
}

/**
 * Transient placeholder for a run the composer started. Carries the {@link runId} so
 * the bubble can link to the run screen and so `useComposerRunReconciler` can swap it
 * for what the run produced.
 *
 * It carries a run and not a job since ADR-068: the composer starts runs, and the job
 * its one step dispatches is an implementation detail of the run — one the chat has no
 * business knowing, and could not follow across a multi-step Studio anyway.
 */
export interface RunPendingChatMessage extends BaseChatMessage {
  kind: "run-pending";
  content: string;
  runId: string;
}

export type ChatMessage =
  TextChatMessage | ImageChatMessage | AudioChatMessage | VideoChatMessage | RunPendingChatMessage;

/**
 * Thread item metadata representing a single conversation history session.
 */
export interface ChatThread {
  conversationId: string;
  title: string;
  updatedAt: number;
}
