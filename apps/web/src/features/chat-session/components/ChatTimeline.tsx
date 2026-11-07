import React from "react";
import { ChatMessage as ChatMessageType } from "@/core/types/chat.types";
import { ChatMessage } from "./ChatMessage";
import { ToolMetricsCard, parseToolMetrics } from "./ToolMetricsCard";
import { ThinkingPipeline } from "./ThinkingPipeline";
import { useTranslation } from "@/core/context/LocaleContext";
import type { ChatPipelineSchema } from "@/core/types/pipeline.types";

interface Props {
  messages: ChatMessageType[];
  isLoadingMessages: boolean;
  isGenerating: boolean;
  isImagePending: boolean;
  isSpeechPending: boolean;
  error: Error | null;
  messagesEndRef: React.RefObject<HTMLDivElement | null>;
  chatPipeline?: ChatPipelineSchema | null;
  completedStageIds?: readonly string[];
}

export const ChatTimeline: React.FC<Props> = ({
  messages,
  isLoadingMessages,
  isGenerating,
  isImagePending,
  isSpeechPending,
  error,
  messagesEndRef,
  chatPipeline,
  completedStageIds,
}) => {
  const { t } = useTranslation();

  if (isLoadingMessages) {
    return (
      <div className="h-full flex items-center justify-center">
        <span className="text-fg-muted text-[13px] animate-pulse">{t.chat.loadingMessages}</span>
      </div>
    );
  }

  if (!messages || messages.length === 0) {
    return (
      <div className="h-full flex items-center justify-center text-fg-muted text-[13px]">
        {t.chat.noActiveConversation}
      </div>
    );
  }

  return (
    <>
      {messages.map((msg, idx) => {
        const toolPayload = msg.content ? parseToolMetrics(msg.content) : null;
        return (
          <React.Fragment key={msg.id}>
            <ChatMessage message={msg} index={idx} streaming={isGenerating && idx === messages.length - 1} />
            {toolPayload && <ToolMetricsCard payload={toolPayload} label={t.chat.toolMetrics} />}
          </React.Fragment>
        );
      })}
      {(isGenerating || isImagePending || isSpeechPending) && (
        <ThinkingPipeline t={t} chatPipeline={chatPipeline} completedStageIds={completedStageIds} />
      )}
      <div ref={messagesEndRef} />
      {error && (
        <div className="p-4 bg-danger/5 text-danger rounded-xl text-[13px] border border-danger/20">
          {t.chat.connectionError}
        </div>
      )}
    </>
  );
};
