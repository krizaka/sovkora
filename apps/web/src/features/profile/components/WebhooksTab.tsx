"use client";

import { WebhooksSection } from "./WebhooksSection";
import { useSettingsHeaders } from "@/features/profile/hooks/useSettingsHeaders";

/** Webhooks section — the endpoints that are told when a run is over. */
export function WebhooksTab() {
  const fetchHeaders = useSettingsHeaders();
  return <WebhooksSection fetchHeaders={fetchHeaders} />;
}
