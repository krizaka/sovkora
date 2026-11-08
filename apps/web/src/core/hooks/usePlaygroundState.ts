import * as React from "react";
import { useState } from "react";

export interface PlaygroundState {
  playgroundInputs: Record<string, Record<string, string>>;
  setPlaygroundInput: (nodeId: string, field: string, value: string) => void;
  playgroundResults: Record<string, unknown>;
  setPlaygroundResult: (nodeId: string, result: unknown) => void;
  activeJobIdByNodeId: Record<string, string>;
  setActiveJobIdForNode: (nodeId: string, jobId: string | null) => void;
}

/** Per-node playground inputs/results/active-job state for the dashboard playground. */
export function usePlaygroundState(): PlaygroundState {
  const [playgroundInputs, setPlaygroundInputs] = useState<Record<string, Record<string, string>>>({});
  const [playgroundResults, setPlaygroundResults] = useState<Record<string, unknown>>({});
  const [activeJobIdByNodeId, setActiveJobIdByNodeId] = useState<Record<string, string>>({});

  const setPlaygroundInput = React.useCallback((nodeId: string, field: string, value: string) => {
    setPlaygroundInputs((prev) => ({
      ...prev,
      [nodeId]: {
        ...prev[nodeId],
        [field]: value,
      },
    }));
  }, []);

  const setPlaygroundResult = React.useCallback((nodeId: string, result: unknown) => {
    setPlaygroundResults((prev) => ({
      ...prev,
      [nodeId]: result,
    }));
  }, []);

  const setActiveJobIdForNode = React.useCallback((nodeId: string, jobId: string | null) => {
    setActiveJobIdByNodeId((prev) => {
      if (jobId === null) {
        const next = { ...prev };
        delete next[nodeId];
        return next;
      }
      return {
        ...prev,
        [nodeId]: jobId,
      };
    });
  }, []);

  return {
    playgroundInputs,
    setPlaygroundInput,
    playgroundResults,
    setPlaygroundResult,
    activeJobIdByNodeId,
    setActiveJobIdForNode,
  };
}
