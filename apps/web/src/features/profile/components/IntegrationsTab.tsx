"use client";

import { AiProvidersSection } from "./AiProvidersSection";
import { McpServersSection } from "./McpServersSection";
import { useSettingsHeaders } from "@/features/profile/hooks/useSettingsHeaders";

/**
 * Integrations section — AI providers and MCP servers. Regular users manage their own remote MCP servers; admins
 * additionally see platform-wide and local (STDIO) transports via {@link McpServersSection}.
 */
export function IntegrationsTab() {
  const fetchHeaders = useSettingsHeaders();
  return (
    <>
      <AiProvidersSection fetchHeaders={fetchHeaders} />
      <McpServersSection fetchHeaders={fetchHeaders} />
    </>
  );
}
