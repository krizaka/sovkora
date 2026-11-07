import { useCallback, useEffect, useState } from "react";
import { apiRequest } from "../config/api";
import { useToken } from "./AuthContext";
import { useT } from "../i18n";
import { errorText } from "../i18n/errorText";

interface ApiResource<T> {
  readonly data: T | null;
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly reload: () => void;
}

/**
 * Loads one authenticated GET into loading / data / error, with a retry.
 *
 * Every list screen asks the same three questions, so they are answered once here rather than
 * five times with five slightly different spinners — the drift that had already happened on the
 * web side before it was factored into `StudioAsyncState`.
 *
 * @param path the API path, or null to load nothing (a detail screen with no id yet)
 * @returns the resource state
 */
export function useApi<T>(path: string | null): ApiResource<T> {
  const token = useToken();
  const t = useT();
  const [data, setData] = useState<T | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);

  useEffect(() => {
    if (path === null) {
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    void (async () => {
      try {
        const result = await apiRequest<T>(path, { token });
        if (!cancelled) {
          setData(result);
        }
      } catch (cause: unknown) {
        if (!cancelled) {
          setError(errorText(cause, t, "common.unavailable"));
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    })();

    // The flag stops a slow response from writing into an unmounted screen — the classic way a
    // list ends up showing the previous screen's data for a frame.
    return () => {
      cancelled = true;
    };
  }, [path, token, attempt, t]);

  return { data, isLoading, error, reload };
}
