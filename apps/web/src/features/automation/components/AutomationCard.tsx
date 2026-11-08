"use client";

import * as React from "react";
import { TrashIcon } from "@krizaka/icons";
import { Button, IconButton } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { Chip } from "@krizaka/ui/chip";
import { Switch } from "@krizaka/ui/switch";
import { Tabs } from "@krizaka/ui/tabs";
import type { Automation } from "@krizaka/orazaka-shared";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { RunHistory } from "./RunHistory";
import { TriggerPanel } from "./TriggerPanel";

/** One automation: its name, the Studio it runs, an enabled switch, and — opened — its triggers and runs. */
export function AutomationCard({
  automation,
  studioLabel,
  busy,
  onEnabled,
  onDelete,
}: Readonly<{
  automation: Automation;
  studioLabel: string;
  busy: boolean;
  onEnabled: (enabled: boolean) => void;
  onDelete: () => void;
}>) {
  const { t } = useTranslation();
  const a = t.automations;
  const [open, setOpen] = React.useState(false);
  return (
    <Card.Root>
      <Card.Body padding="md" className="gap-3">
        <header className="flex flex-wrap items-center gap-3">
          <span className="flex min-w-0 flex-1 flex-col gap-1">
            <Card.Title className="line-clamp-none text-[15px]">{automation.name}</Card.Title>
            <span className="flex flex-wrap items-center gap-2 text-xs text-fg-secondary">
              <Chip>{fill(a.runsStudio, { studio: studioLabel })}</Chip>
            </span>
          </span>
          <Switch label={a.enabledLabel} checked={automation.enabled} disabled={busy} onCheckedChange={onEnabled} />
          <IconButton variant="ghost" size="sm" label={a.delete} disabled={busy} onClick={onDelete}>
            <TrashIcon size={15} />
          </IconButton>
        </header>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="self-start"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          {open ? a.hideDetails : a.showDetails}
        </Button>
        {open && (
          // @ephemeral: the tabs of an unfolded card, closed with the card
          <Tabs.Root defaultValue="triggers">
            <Tabs.List>
              <Tabs.Trigger value="triggers">{a.tabs.triggers}</Tabs.Trigger>
              <Tabs.Trigger value="runs">{a.tabs.runs}</Tabs.Trigger>
            </Tabs.List>
            <Tabs.Content value="triggers" className="pt-3">
              <TriggerPanel automationId={automation.id} />
            </Tabs.Content>
            <Tabs.Content value="runs" className="pt-3">
              <RunHistory automationId={automation.id} />
            </Tabs.Content>
          </Tabs.Root>
        )}
      </Card.Body>
    </Card.Root>
  );
}
