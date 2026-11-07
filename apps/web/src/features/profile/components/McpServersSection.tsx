"use client";

import * as React from "react";
import { Input, Button } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { useMcpServers } from "@/features/profile/hooks/useMcpServers";

import { SettingsSection } from "@krizaka/ui/settings-layout";

const selectClass =
  "flex h-10 w-full rounded-md border border-border-subtle bg-surface-1 px-3 py-2 text-sm text-fg focus:outline-none focus:ring-1 focus:ring-border-subtle";
const inputClass = "bg-surface-1 border-border-subtle text-fg";
const labelClass = "text-sm font-medium text-fg opacity-80";

interface McpServersSectionProps {
  fetchHeaders: () => Promise<Record<string, string>>;
}

/**
 * McpServersSection renders the MCP server registration UI (list + add/delete form). All data and
 * lifecycle logic live in {@link useMcpServers}; this component is purely presentational.
 *
 * @param props.fetchHeaders - Async function returning authorization headers.
 */
export function McpServersSection({ fetchHeaders }: Readonly<McpServersSectionProps>) {
  const { t } = useTranslation();
  const {
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
  } = useMcpServers(fetchHeaders);

  return (
    <SettingsSection
      variant="flat"
      id="integrations-mcp"
      title={t.settings.mcpServersTitle}
      description={t.settings.mcpServersDesc}
    >
      {serverMessage && (
        <div className="p-3 bg-success/20 border border-success/40 rounded-xl text-xs font-semibold text-success">
          {serverMessage}
        </div>
      )}
      <section>
        {(() => {
          if (isLoadingServers) {
            return <p className="py-4 text-xs text-fg-muted">{t.settings.mcpLoading}</p>;
          }
          if (servers.length === 0) {
            return <p className="py-4 text-xs text-fg-muted">{t.settings.mcpNoServers}</p>;
          }
          return (
            <ul className="divide-y divide-border-subtle">
              {servers.map((srv) => (
                <li key={srv.id} className="flex items-center justify-between gap-3 py-3 text-xs">
                  <article className="space-y-1">
                    <h4 className="font-semibold text-fg flex items-center gap-2">
                      <span>{srv.label}</span>
                      {isAdmin && (
                        <span className="px-1.5 py-0.5 rounded bg-surface-2 text-[10px]">{srv.transportType}</span>
                      )}
                    </h4>
                    <p className="opacity-70 font-mono">{srv.url || srv.command}</p>
                  </article>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => handleDeleteServer(srv.id)}
                    disabled={isSavingServer}
                    className="text-danger border-danger/20 hover:bg-danger/20 px-2 py-1 h-7 text-[10px]"
                  >
                    {t.settings.mcpDelete}
                  </Button>
                </li>
              ))}
            </ul>
          );
        })()}
      </section>
      <form onSubmit={handleSaveServer} className="space-y-3 py-4">
        <div className="space-y-2">
          <label className={labelClass}>{t.settings.mcpLabel}</label>
          <Input
            value={serverLabel}
            onChange={(e) => setServerLabel(e.target.value)}
            placeholder={t.settings.mcpLabelPlaceholder}
            className={inputClass}
            required
          />
        </div>
        <div className="space-y-2">
          <label className={labelClass}>{t.settings.mcpTransportType}</label>
          <select
            className={selectClass}
            value={serverTransport}
            onChange={(e) => setServerTransport(e.target.value as "remote" | "local")}
            disabled={!isAdmin}
          >
            <option value="remote">{t.settings.mcpRemoteLabel}</option>
            {isAdmin && <option value="local">{t.settings.mcpLocalLabel}</option>}
          </select>
        </div>
        {serverTransport === "remote" ? (
          <>
            <div className="space-y-2">
              <label className={labelClass}>{t.settings.mcpUrl}</label>
              <Input
                value={serverUrl}
                onChange={(e) => setServerUrl(e.target.value)}
                placeholder={t.settings.mcpUrlPlaceholder}
                className={inputClass}
                required
              />
            </div>
            <div className="space-y-2">
              <label className={labelClass}>{t.settings.mcpAuthToken}</label>
              <Input
                type="password"
                value={serverAuthToken}
                onChange={(e) => setServerAuthToken(e.target.value)}
                placeholder={t.settings.mcpAuthTokenPlaceholder}
                className={inputClass}
              />
            </div>
          </>
        ) : (
          <>
            <div className="space-y-2">
              <label className={labelClass}>{t.settings.mcpCommand}</label>
              <Input
                value={serverCommand}
                onChange={(e) => setServerCommand(e.target.value)}
                placeholder={t.settings.mcpCommandPlaceholder}
                className={inputClass}
                required
              />
            </div>
            <div className="space-y-2">
              <label className={labelClass}>{t.settings.mcpArgs}</label>
              <Input
                value={serverArgs}
                onChange={(e) => setServerArgs(e.target.value)}
                placeholder={t.settings.mcpArgsPlaceholder}
                className={inputClass}
              />
            </div>
          </>
        )}
        <Button
          type="submit"
          disabled={isSavingServer || !serverLabel || (serverTransport === "remote" ? !serverUrl : !serverCommand)}
          className="mt-2"
        >
          {t.settings.mcpRegister}
        </Button>
      </form>
    </SettingsSection>
  );
}
