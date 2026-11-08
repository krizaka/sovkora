"use client";

import { keepPreviousData, useQuery } from "@krizaka/query";
import { AdminRefusal } from "@/console/services/platform.admin.api";

/** What a law read left: its data, whether it is on its way, the refusal it met (status and Problem code), a reload. */
export interface LawLoad<T> {
  data: T;
  isLoading: boolean;
  refusal: AdminRefusal | null;
  reload: () => void;
}

/** A refusal as the screen words it: an `AdminRefusal`, or a network failure read as a 503 without a code. */
export const asRefusal = (error: unknown) => (error instanceof AdminRefusal ? error : new AdminRefusal(503, null));

/** A stable number per `load` function: the query is keyed by which read it is (a status, a kind), never by a closure. */
const loadIds = new WeakMap<object, number>();
let lastLoadId = 0;
const loadId = (load: object): number => {
  let id = loadIds.get(load);
  if (id === undefined) {
    id = ++lastLoadId;
    loadIds.set(load, id);
  }
  return id;
};

/**
 * One read of the law surface, reloaded when `load` changes (it is memoised on what it reads: a status, a kind), when
 * `refresh` changes (a decision elsewhere on the screen) or on demand — the queue changes only when someone acts.
 * A read that is overtaken by the next one is dropped (the query is keyed by the read), never shown over it.
 */
export function useLawLoad<T>(load: (() => Promise<T>) | null, initial: T, refresh = 0): LawLoad<T> {
  const query = useQuery({
    queryKey: ["law", load ? loadId(load) : 0, refresh],
    queryFn: () => (load as () => Promise<T>)(),
    enabled: load !== null,
    placeholderData: keepPreviousData,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });
  return {
    data: query.data ?? initial,
    isLoading: load !== null && query.isFetching,
    refusal: query.error ? asRefusal(query.error) : null,
    reload: () => void query.refetch(),
  };
}
