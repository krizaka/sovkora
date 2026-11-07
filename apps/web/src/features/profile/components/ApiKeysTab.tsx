"use client";

import { ApiKeysSection } from "./ApiKeysSection";
import { useSettingsHeaders } from "@/features/profile/hooks/useSettingsHeaders";

/** API keys section — the user's personal access tokens. */
export function ApiKeysTab() {
  const fetchHeaders = useSettingsHeaders();
  return <ApiKeysSection fetchHeaders={fetchHeaders} />;
}
