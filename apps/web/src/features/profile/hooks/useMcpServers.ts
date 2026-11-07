"use client";

import * as React from "react";
import { useQuery, useQueryClient } from "@krizaka/query";
import { useTranslation } from "@/core/context/LocaleContext";
import { useAuth } from "@/core/hooks/useAuth";

export interface McpServerInfo {
  id: number;
  label: string;
  transportType?: string;
  url?: string | null;
  command?: string | null;
  args?: string | null;
  authToken?: string | null;
  enabled?: boolean;
  userId?: string;
}

/**
 * Encapsulates the MCP server registration lifecycle (load / create / delete) and its form state,
 * keeping {@link McpServersSection} a presentational component. Admin users target the platform-wide
 * registry (with transport-type options); regular users target their own remote SSE registrations.
 *
 * @param fetchHeaders - Async function returning authorization headers.
 */
export function useMcpServers(fetchHeaders: () => Promise<Record<string, string>>) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const isAdmin = user?.role === "admin";

  const [serverLabel, setServerLabel] = React.useState("");
  const [serverUrl, setServerUrl] = React.useState("");
  const [serverAuthToken, setServerAuthToken] = React.useState("");
  const [serverTransport, setServerTransport] = React.useState<"remote" | "local">("remote");
  const [serverCommand, setServerCommand] = React.useState("");
  const [serverArgs, setServerArgs] = React.useState("");
  const [serverMessage, setServerMessage] = React.useState<string | null>(null);
  const [isSavingServer, setIsSavingServer] = React.useState(false);

  const queryClient = useQueryClient();
  // The list is keyed by scope: the platform's servers for an admin, the user's own otherwise.
  const serversQuery = useQuery({
    queryKey: ["mcp-servers", isAdmin ? "platform" : "user"],
    enabled: Boolean(user),
    queryFn: async () => {
      const endpoint = isAdmin ? "/api/v1/mcp/servers/platform" : "/api/v1/mcp/servers/user";
      const res = await fetch(endpoint, { headers: await fetchHeaders() });
      if (!res.ok) throw new Error(`mcp servers ${res.status}`);
      return (await res.json()) as McpServerInfo[];
    },
  });
  const servers = serversQuery.data ?? [];
  const isLoadingServers = serversQuery.isFetching;
  const loadServers = () => queryClient.invalidateQueries({ queryKey: ["mcp-servers"] });

  const handleSaveServer = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!serverLabel) return;
    setIsSavingServer(true);
    setServerMessage(null);
    try {
      const headers = await fetchHeaders();
      const endpoint = isAdmin ? "/api/v1/mcp/servers/platform" : "/api/v1/mcp/servers/user";
      const body = (() => {
        if (isAdmin) {
          return {
            label: serverLabel,
            transportType: serverTransport.toUpperCase(),
            url: serverTransport === "remote" ? serverUrl : null,
            command: serverTransport === "local" ? serverCommand : null,
            args: serverTransport === "local" ? serverArgs : null,
            authToken: serverTransport === "remote" ? serverAuthToken : null,
            enabled: true,
          };
        }
        return {
          userId: user?.id || "",
          label: serverLabel,
          url: serverUrl,
          authToken: serverAuthToken || null,
          enabled: true,
        };
      })();

      const res = await fetch(endpoint, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });

      if (res.ok) {
        setServerMessage(t.settings.mcpSuccess);
        setServerLabel("");
        setServerUrl("");
        setServerAuthToken("");
        setServerCommand("");
        setServerArgs("");
        loadServers();
      } else {
        const errorText = await res.text();
        setServerMessage(`${t.settings.mcpError}: ${errorText}`);
      }
    } catch {
      setServerMessage(t.settings.mcpError);
    } finally {
      setIsSavingServer(false);
    }
  };

  const handleDeleteServer = async (id: number) => {
    setIsSavingServer(true);
    setServerMessage(null);
    try {
      const headers = await fetchHeaders();
      const endpoint = isAdmin ? `/api/v1/mcp/servers/platform/${id}` : `/api/v1/mcp/servers/user/${id}`;
      const res = await fetch(endpoint, { method: "DELETE", headers });
      setServerMessage(res.ok ? t.settings.mcpDeleteSuccess : t.settings.mcpDeleteError);
      if (res.ok) loadServers();
    } catch {
      setServerMessage(t.settings.mcpDeleteError);
    } finally {
      setIsSavingServer(false);
    }
  };

  return {
    isAdmin,
    servers,
    isLoadingServers,
    isSavingServer,
    serverMessage,
    serverLabel,
    setServerLabel,
    serverUrl,
    setServerUrl,
    serverAuthToken,
    setServerAuthToken,
    serverTransport,
    setServerTransport,
    serverCommand,
    setServerCommand,
    serverArgs,
    setServerArgs,
    handleSaveServer,
    handleDeleteServer,
  };
}
