import { Badge } from "@krizaka/ui/badge";
import React from "react";
import type { ChatMessage as ChatMessageType } from "@/core/types/chat.types";
import { useTranslation } from "@/core/context/LocaleContext";
import { controlLabel } from "@/core/utils/control";

/** « En vérification » while the engine checks the answer, then its verdict; a degraded verification says so. */
export const ControlBadge: React.FC<Readonly<{ message: ChatMessageType; streaming: boolean }>> = ({
  message,
  streaming,
}) => {
  const { t } = useTranslation();
  const label = controlLabel(message.control, streaming);
  if (!label) return null;
  const tone = { pending: "neutral", validated: "success", unavailable: "warning" } as const;
  return (
    <Badge
      tone={tone[label]}
      dot
      pulse={label === "pending"}
      data-testid={`chat-control-${label}`}
      role="status"
      title={label === "unavailable" ? t.control.unavailableHint : undefined}
      className="mt-2"
    >
      {t.control[label]}
    </Badge>
  );
};
