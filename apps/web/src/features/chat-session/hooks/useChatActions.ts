"use client";

import { useState, useRef, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/core/hooks/useAuth";
import { useJobStream } from "@/core/context/JobStreamContext";
import type { ChatMessage } from "@/core/types/chat.types";
import type { ComposerStudio } from "@krizaka/orazaka-shared";
import { startComposerRun, type ComposerRunOptions } from "@/features/chat-session/utils/startComposerRun";
import { saveStoredMessages } from "@/features/chat-session/hooks/useMessageHistory";
import { useComposerRunReconciler } from "@/features/chat-session/hooks/useComposerRunReconciler";
import { fileToThumbnail } from "@/features/chat-session/utils/imageThumbnail";
import { MediaApi } from "@/services/media.api";

/** A Studio whose one input holds an asset cannot run without one — it says so itself. */
const requiresAttachment = (studio: ComposerStudio | null): boolean => !!studio && studio.inputKind === "ASSET";

interface UseChatActionsProps {
  activeConversationId: string;
}

/**
 * Custom hook encapsulating chat action handlers: thread management,
 * file uploads, message sending, and launching a run from the composer.
 * Extracts imperative logic from ChatWindow into a composable unit.
 */
export function useChatActions({ activeConversationId }: UseChatActionsProps) {
  const router = useRouter();
  const { user } = useAuth();
  const { setActiveConversationId, chatInput, setChatInput } = useJobStream();
  const queryClient = useQueryClient();

  const userId = user?.id || user?.email || "anonymous";
  const { trackRun } = useComposerRunReconciler(userId, activeConversationId);

  const [selectedStudio, setSelectedStudio] = useState<ComposerStudio | null>(null);
  const [selectedOptions, setSelectedOptions] = useState<ComposerRunOptions>({});
  const [attachment, setAttachment] = useState<{
    assetId: string;
    name: string;
    previewUrl?: string;
  } | null>(null);
  const [isUploadingAttachment, setIsUploadingAttachment] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const appendToCache = useCallback(
    (message: ChatMessage, conversationId: string) => {
      const cacheKey = ["chatMessages", userId, conversationId];
      queryClient.setQueryData<ChatMessage[]>(cacheKey, (old = []) => {
        const updated = [...old, message];
        saveStoredMessages(userId, conversationId, updated);
        return updated;
      });
    },
    [queryClient, userId],
  );

  const nodeMutation = useMutation({
    // `conversationId` names the thread the run belongs to when it is not the active one yet: the
    // first message of a new conversation creates the thread and runs in it in the same gesture.
    mutationFn: ({
      conversationId: _thread,
      ...run
    }: Parameters<typeof startComposerRun>[0] & { conversationId?: string }) => startComposerRun(run),
    // A run is asynchronous, so the text path's user-bubble echo is bypassed. Mirror
    // it here (covers both handleSend and handleExecuteNode) so the user's request
    // appears in the timeline, not just the acknowledgement.
    onMutate: (vars) =>
      appendToCache(
        {
          id: `user-${Date.now()}`,
          role: "user",
          content: vars.prompt,
          timestamp: Date.now(),
          kind: "text",
          // Preserve a visible trace of an attached image (e.g. for analysis).
          ...(attachment?.previewUrl
            ? {
                attachment: {
                  assetId: attachment.assetId,
                  name: attachment.name,
                  url: attachment.previewUrl,
                },
              }
            : {}),
        },
        vars.conversationId ?? activeConversationId,
      ),
    onSuccess: (run, vars) => {
      const messageId = `assistant-${Date.now()}`;
      if (!run) {
        return;
      }
      // Render a "generating" placeholder linking to the run, and follow the run so
      // what it produced is swapped into this message when it finishes.
      const thread = vars.conversationId ?? activeConversationId;
      appendToCache(
        { id: messageId, role: "assistant", content: "", timestamp: Date.now(), kind: "run-pending", runId: run.id },
        thread,
      );
      trackRun(run.id, thread, messageId);
    },
  });

  const handleSelectThread = useCallback(
    (id: string) => {
      setActiveConversationId(id);
      router.push(`/chat?conversationId=${id}`);
    },
    [setActiveConversationId, router],
  );

  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>, t: { errors: { fileUploadFailed: string } }) => {
      const file = e.target.files?.[0];
      if (!file) return;
      setIsUploadingAttachment(true);
      try {
        const res = await MediaApi.uploadMedia(file);
        // Keep a small inline preview so the user's message shows what was sent.
        let previewUrl = "";
        try {
          previewUrl = await fileToThumbnail(file);
        } catch {
          // Preview is best-effort; a failure must not block the upload.
        }
        setAttachment({ assetId: res.assetId, name: file.name, previewUrl });
      } catch (err) {
        console.error("Failed to upload attachment:", err);
        alert(t.errors.fileUploadFailed);
      } finally {
        setIsUploadingAttachment(false);
        if (fileInputRef.current) fileInputRef.current.value = "";
      }
    },
    [],
  );

  const handleSend = useCallback(
    (sendMessage: (msg: string) => void, isSending: boolean, isGenerating: boolean) => {
      const trimmed = chatInput.trim();
      if (
        (!trimmed && !attachment && !selectedStudio) ||
        isSending ||
        isGenerating ||
        isUploadingAttachment ||
        // Analysis capabilities can't run without a staged asset.
        (requiresAttachment(selectedStudio) && !attachment)
      )
        return;

      if (selectedStudio) {
        nodeMutation.mutate({
          studio: selectedStudio,
          prompt: trimmed || `Run ${selectedStudio.label}`,
          assetId: attachment?.assetId,
          options: selectedOptions,
        });
        setSelectedStudio(null);
        setSelectedOptions({});
      } else {
        let finalPrompt = trimmed;
        if (attachment) {
          if (!finalPrompt) {
            finalPrompt = `Analyze attachment: ${attachment.name}`;
          }
          finalPrompt = `${finalPrompt} (assetId: ${attachment.assetId})`;
        }
        sendMessage(finalPrompt);
      }
      setChatInput("");
      setAttachment(null);
    },
    [chatInput, attachment, selectedStudio, selectedOptions, isUploadingAttachment, nodeMutation, setChatInput],
  );

  const handleExecuteNode = useCallback(
    (studio: ComposerStudio, isPlusMenuOpen: boolean, setIsPlusMenuOpen: (v: boolean) => void) => {
      // Stage (don't immediately run) when there's no prompt yet, or when the
      // capability needs an asset the user hasn't attached — so options/attach show.
      if (!chatInput.trim() || requiresAttachment(studio)) {
        setSelectedStudio(studio);
        setSelectedOptions({});
        setIsPlusMenuOpen(false);
        return;
      }
      nodeMutation.mutate({
        studio,
        prompt: chatInput.trim(),
        assetId: attachment?.assetId,
        options: selectedOptions,
      });
      setChatInput("");
      setAttachment(null);
      setIsPlusMenuOpen(false);
    },
    [chatInput, attachment, selectedOptions, nodeMutation, setChatInput],
  );

  /**
   * Runs the staged Studio in a thread that was just created (the first message of a conversation).
   * Returns false when nothing is staged, so the caller sends a chat message instead.
   */
  const runStagedIn = useCallback(
    (conversationId: string): boolean => {
      if (!selectedStudio) return false;
      nodeMutation.mutate({
        studio: selectedStudio,
        prompt: chatInput.trim() || `Run ${selectedStudio.label}`,
        assetId: attachment?.assetId,
        options: selectedOptions,
        conversationId,
      });
      setSelectedStudio(null);
      setSelectedOptions({});
      setAttachment(null);
      return true;
    },
    [selectedStudio, chatInput, attachment, selectedOptions, nodeMutation],
  );

  const updateOptions = useCallback(
    (patch: Partial<ComposerRunOptions>) => setSelectedOptions((prev) => ({ ...prev, ...patch })),
    [],
  );

  const clearStudio = useCallback(() => {
    setSelectedStudio(null);
    setSelectedOptions({});
  }, []);

  return {
    userId,
    selectedStudio,
    setSelectedFeature: setSelectedStudio,
    selectedOptions,
    updateOptions,
    clearStudio,
    attachment,
    setAttachment,
    isUploadingAttachment,
    fileInputRef,
    nodeMutation,
    handleSelectThread,
    handleFileChange,
    handleSend,
    handleExecuteNode,
    runStagedIn,
  };
}
