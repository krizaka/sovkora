"use client";

import { Card } from "@krizaka/ui/card";
import React from "react";
import { Icon, SentinelMini } from "@krizaka/orazaka-design-system";
import type { IconName } from "@krizaka/orazaka-design-system";
import { Greeting } from "@/core/components/Greeting";
import type { TranslationDictionary } from "@/core/context/LocaleContext";

interface WelcomeHeroProps {
  t: TranslationDictionary;
  /** Submit a starter prompt (creates the thread + sends). */
  onPrompt: (prompt: string) => void;
}

/**
 * WelcomeHero — branded, calm entry point for a new conversation.
 *
 * Replaces the old typewriter/emoji treatment with the Sentinel brand mark, a
 * time-of-day greeting from the dictionary, and three actionable prompt cards that submit on
 * click. No "AI" cliché, reduced-motion safe (entrance only).
 */
export function WelcomeHero({ t, onPrompt }: Readonly<WelcomeHeroProps>) {
  const cards: { label: string; icon: IconName }[] = [
    { label: t.chat.suggestionImage, icon: "image" },
    { label: t.chat.suggestionCode, icon: "code" },
    { label: t.chat.suggestionAsk, icon: "chat" },
  ];

  return (
    <div className="flex w-full max-w-2xl flex-col items-center px-6 text-center motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-3 motion-safe:duration-500">
      {/* Brand mark */}
      <div className="relative mb-6 flex h-16 w-16 items-center justify-center rounded-2xl border border-border-subtle bg-surface-1 shadow-sm">
        <span className="absolute inset-0 rounded-2xl bg-accent opacity-[0.06] blur-xl" />
        <SentinelMini size={34} />
      </div>

      <h2 className="text-xl font-bold tracking-tight text-fg">
        <Greeting suffix="." />
      </h2>
      <p className="mt-2 text-sm text-fg-muted">{t.chat.startConversationDesc}</p>

      {/* Actionable prompt cards */}
      <div className="mt-8 grid w-full grid-cols-1 gap-3 stagger-children sm:grid-cols-3">
        {cards.map(({ label, icon }) => (
          <Card.Root key={label} asChild interactive>
            <button type="button" onClick={() => onPrompt(label)} className="text-left">
              <Card.Body className="gap-3">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent-soft text-fg-accent">
                  <Icon name={icon} className="h-4 w-4" />
                </span>
                <span className="text-sm font-medium text-fg">{label}</span>
              </Card.Body>
            </button>
          </Card.Root>
        ))}
      </div>
    </div>
  );
}
