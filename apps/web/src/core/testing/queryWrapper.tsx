import { createElement, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@krizaka/query";

/** A fresh query client for a test: no retry, no cache between tests, so a failure shows at once. */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } });
}

/** The wrapper a hook test passes to `renderHook` (or `render`) when the hook reads server state. */
export function queryWrapper(client: QueryClient = createTestQueryClient()) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client }, children);
  };
}
