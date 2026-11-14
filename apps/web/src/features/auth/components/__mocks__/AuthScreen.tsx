import type { ReactNode } from "react";

/** A bare screen for the tests of the pages that sit inside it: its title and its content, no chrome. */
export function AuthScreen({ title, children }: Readonly<{ title?: string; children: ReactNode }>) {
  return (
    <main>
      {title ? <h1>{title}</h1> : null}
      {children}
    </main>
  );
}
