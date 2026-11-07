import { Suspense } from "react";
import type { Metadata } from "next";
import { StudiosScreen } from "./StudiosScreen";

export const metadata: Metadata = {
  title: "Studios | Sovkora",
  description: "Every Studio in one list: what your plan makes ready, what a pack would add, and one action for each.",
};

/**
 * The Studios section: one list, its filters in the address (`?q=`, `?show=`, `?trade=`, `?pack=`).
 *
 * A thin route shell. The Suspense boundary is what `useSearchParams` asks for: the shell renders statically and the
 * list reads its address on the client.
 */
export default function StudiosPage() {
  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-6 sm:px-6">
      <Suspense>
        <StudiosScreen />
      </Suspense>
    </main>
  );
}
