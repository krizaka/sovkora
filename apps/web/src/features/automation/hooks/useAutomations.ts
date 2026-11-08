"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AutomationRunStatusSchema } from "@krizaka/orazaka-shared";
import { AutomationApi, type NewAutomation, type NewTrigger } from "@/services/automation.api";
import { StudioApi } from "@/services/studio.api";
import { useTranslation } from "@/core/context/LocaleContext";

/** The states of a run that are not over: the history refreshes by itself while one is in flight. */
const IN_FLIGHT: ReadonlySet<string> = new Set([
  AutomationRunStatusSchema.enum.queued,
  AutomationRunStatusSchema.enum.running,
]);

/** Query keys: one place, so a mutation invalidates exactly what it changed. */
export const automationKeys = {
  all: ["automations"] as const,
  triggers: (id: string) => ["automations", id, "triggers"] as const,
  runs: (id: string) => ["automations", id, "runs"] as const,
};

/** The caller's automations. */
export function useAutomations() {
  return useQuery({ queryKey: automationKeys.all, queryFn: AutomationApi.list });
}

/** Create, change and remove automations; each refreshes the list. */
export function useAutomationMutations() {
  const client = useQueryClient();
  const refresh = () => client.invalidateQueries({ queryKey: automationKeys.all });
  return {
    create: useMutation({ mutationFn: (draft: NewAutomation) => AutomationApi.create(draft), onSuccess: refresh }),
    setEnabled: useMutation({
      mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => AutomationApi.update(id, { enabled }),
      onSuccess: refresh,
    }),
    remove: useMutation({ mutationFn: (id: string) => AutomationApi.remove(id), onSuccess: refresh }),
  };
}

/** The triggers of one automation, read when its detail is open. */
export function useTriggers(automationId: string) {
  return useQuery({
    queryKey: automationKeys.triggers(automationId),
    queryFn: () => AutomationApi.triggers(automationId),
  });
}

/**
 * Add, enable, rotate and remove the triggers of an automation. `add` and `rotate` resolve with the trigger that
 * carries its one-time secret: the caller shows it and drops it.
 */
export function useTriggerMutations(automationId: string) {
  const client = useQueryClient();
  const refresh = () => client.invalidateQueries({ queryKey: automationKeys.triggers(automationId) });
  return {
    add: useMutation({
      mutationFn: (trigger: NewTrigger) => AutomationApi.addTrigger(automationId, trigger),
      onSuccess: refresh,
    }),
    setEnabled: useMutation({
      mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
        AutomationApi.setTriggerEnabled(automationId, id, enabled),
      onSuccess: refresh,
    }),
    rotate: useMutation({
      mutationFn: (id: string) => AutomationApi.rotateTrigger(automationId, id),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: string) => AutomationApi.removeTrigger(automationId, id),
      onSuccess: refresh,
    }),
  };
}

/**
 * The runs of one automation, newest first. While one is queued or running the list refreshes by itself, so a run
 * started by a webhook seconds ago turns from « running » to « succeeded » on screen.
 */
export function useAutomationRuns(automationId: string) {
  return useQuery({
    queryKey: automationKeys.runs(automationId),
    queryFn: () => AutomationApi.runs(automationId),
    refetchInterval: (query) => (query.state.data?.some((run) => IN_FLIGHT.has(run.status)) ? 3000 : false),
  });
}

/** The Studios an automation can run: the ones the caller is not locked out of. */
export function useRunnableStudios() {
  const { locale } = useTranslation();
  return useQuery({
    queryKey: ["automations", "studios", locale],
    queryFn: async () => (await StudioApi.fetchCatalogue(null, locale)).filter((studio) => !studio.locked),
  });
}
