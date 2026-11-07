"use client";

import { useState } from "react";
import {
  AgentIcon,
  ChatIcon,
  FlagIcon,
  MessageIcon,
  NotificationIcon,
  SettingsIcon,
  type IconProps,
} from "@krizaka/icons";
import { Badge } from "@krizaka/ui/badge";
import { Button, IconButton } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { Field, Input } from "@krizaka/ui/field";
import { useTranslation } from "@/core/context/LocaleContext";
import type { AutomationDictionary } from "@/core/context/translations.types";

type ConnectorId = keyof AutomationDictionary["connectors"];
type ConnectorStatus = keyof AutomationDictionary["status"];

interface Connector {
  id: ConnectorId;
  /** A product name: the same in every language. */
  name: string;
  Icon: React.ComponentType<IconProps>;
  status: ConnectorStatus;
}

/** The connectors on offer. Structure only: their words are `automation.connectors.<id>`. */
const CONNECTORS: Connector[] = [
  { id: "jira", name: "Jira Cloud", Icon: FlagIcon, status: "disconnected" },
  { id: "whatsapp", name: "WhatsApp Business", Icon: MessageIcon, status: "disconnected" },
  { id: "messenger", name: "Messenger", Icon: ChatIcon, status: "disconnected" },
  { id: "slack", name: "Slack", Icon: NotificationIcon, status: "disconnected" },
  { id: "cli-agent", name: "Local CLI Agent", Icon: AgentIcon, status: "disconnected" },
];

const TONE = { connected: "success", disconnected: "neutral", pending: "warning" } as const;

/** A connector's state: the @krizaka/ui badge, its dot in the status colour. */
export function ConnectorStatusBadge({ status }: Readonly<{ status: ConnectorStatus }>) {
  const { t } = useTranslation();
  return (
    <Badge tone={TONE[status]} dot pulse={status === "pending"}>
      {t.automation.status[status]}
    </Badge>
  );
}

/**
 * The connector catalogue: one @krizaka/ui card per tool, its state, connect / disconnect, and the credentials panel.
 * Signature icons (@krizaka/icons), never a third-party brand colour.
 */
export default function ConnectorCatalogue() {
  const { t } = useTranslation();
  const [connectors, setConnectors] = useState(CONNECTORS);
  const [expandedId, setExpandedId] = useState<ConnectorId | null>(null);

  const toggle = (id: ConnectorId) =>
    setConnectors((previous) =>
      previous.map((c) =>
        c.id === id ? { ...c, status: c.status === "connected" ? "disconnected" : "connected" } : c,
      ),
    );

  return (
    <section id="connector-catalogue" className="flex flex-col gap-6 py-8">
      <header className="flex flex-col gap-1">
        <h2 className="hud-title text-2xl text-fg">{t.automation.connectorsTitle}</h2>
        <p className="text-sm text-fg-secondary">{t.automation.connectorsSubtitle}</p>
      </header>

      <ul className="stagger-children grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-4">
        {connectors.map(({ id, name, Icon, status }) => (
          <li key={id} id={`connector-${id}`}>
            <Card.Root className={status === "connected" ? "h-full border-accent/50" : "h-full"}>
              <Card.Body padding="md" className="gap-3.5 p-5">
                <header className="flex items-center gap-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-surface-2 text-fg">
                    <Icon size={20} nodeColor="var(--kz-accent)" />
                  </span>
                  <span className="flex flex-col items-start gap-1">
                    <Card.Title className="line-clamp-none text-[15px] group-hover:text-fg">{name}</Card.Title>
                    <ConnectorStatusBadge status={status} />
                  </span>
                </header>
                <Card.Description className="line-clamp-none text-[13px] leading-relaxed">
                  {t.automation.connectors[id]}
                </Card.Description>
                <footer className="mt-auto flex items-center gap-2">
                  <Button
                    id={`toggle-${id}`}
                    variant={status === "connected" ? "secondary" : "primary"}
                    size="sm"
                    className="flex-1"
                    aria-label={t.automation.toggle.replace("{name}", name)}
                    onClick={() => toggle(id)}
                  >
                    {status === "connected" ? t.automation.disconnect : t.automation.connect}
                  </Button>
                  <IconButton
                    id={`config-${id}`}
                    variant="ghost"
                    size="sm"
                    label={t.automation.configure.replace("{name}", name)}
                    aria-expanded={expandedId === id}
                    onClick={() => setExpandedId(expandedId === id ? null : id)}
                  >
                    <SettingsIcon size={16} />
                  </IconButton>
                </footer>
                {expandedId === id && <CredentialsPanel connectorId={id} />}
              </Card.Body>
            </Card.Root>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** The API key of one connector: a @krizaka/ui field. */
function CredentialsPanel({ connectorId }: Readonly<{ connectorId: ConnectorId }>) {
  const { t } = useTranslation();
  const inputId = `api-key-${connectorId}`;
  return (
    <Field.Root className="border-t border-border-subtle pt-3">
      <Field.Label htmlFor={inputId}>{t.automation.apiKey}</Field.Label>
      <Input id={inputId} type="password" autoComplete="off" placeholder={t.automation.apiKeyPlaceholder} />
      <Button id={`save-${connectorId}`} size="sm" className="self-start">
        {t.automation.saveCredentials}
      </Button>
    </Field.Root>
  );
}
