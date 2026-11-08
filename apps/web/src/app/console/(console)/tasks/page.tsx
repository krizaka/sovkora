import type { Metadata } from "next";
import { TasksScreen } from "@/console/features/tasks/components/TasksScreen";

export const metadata: Metadata = { title: "Tasks · Sovkora Operations" };

/** `/tasks` — what needs an operator: refused or failed actions without a success since, failed jobs (ADR-085). */
export default function TasksPage() {
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
      <TasksScreen />
    </main>
  );
}
