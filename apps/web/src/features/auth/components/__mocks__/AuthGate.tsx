import type { ReactNode } from "react";

/** The gate open: the tests of the pages behind it render their content. */
export function AuthGate({ children }: Readonly<{ children: ReactNode }>) {
  return children;
}
