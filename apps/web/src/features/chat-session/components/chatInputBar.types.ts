import type * as React from "react";
import type { ComposerStudio } from "@krizaka/orazaka-shared";
import type { ComposerRunOptions as Options } from "@/features/chat-session/utils/startComposerRun";
import type { ChatRouting } from "@/core/utils/routing";
import type { TranslationDictionary } from "@/core/context/LocaleContext";

export interface ChatInputBarProps {
  input: string;
  onInputChange: (value: string) => void;
  /** Sends the draft (Enter or the round button). */
  onSend: () => void;
  isSending: boolean;
  isGenerating: boolean;
  isUploadingAttachment: boolean;
  selectedStudio: ComposerStudio | null;
  selectedOptions: Options;
  onOptionsChange: (patch: Partial<Options>) => void;
  attachment: { assetId: string; name: string } | null;
  onClearStudio: () => void;
  onClearAttachment: () => void;
  isPlusMenuOpen: boolean;
  onTogglePlusMenu: () => void;
  onClosePlusMenu: () => void;
  onExecuteNode: (studio: ComposerStudio) => void;
  composerStudios: ComposerStudio[];
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  onFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  /** Where the last answer ran (from the stream), or null when the engine has not said. */
  routing: ChatRouting | null;
  t: TranslationDictionary;
  /** true = centered under the welcome cards (same width), false = docked under the conversation */
  isCentered?: boolean;
}
