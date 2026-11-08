import "@testing-library/jest-dom";
import * as React from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AutomationsSection } from "@/features/automation/components/AutomationsSection";
import { translations } from "@/core/context/translations";
import { AutomationApi } from "@/services/automation.api";
import { StudioApi } from "@/services/studio.api";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));
jest.mock("@/services/automation.api");
jest.mock("@/services/studio.api");

const t = translations.en.automations;
const api = AutomationApi as jest.Mocked<typeof AutomationApi>;
const studios = StudioApi as jest.Mocked<typeof StudioApi>;

const automation = {
  id: "a1",
  name: "Lease check on request",
  studioKey: "compliance-check",
  inputs: {},
  enabled: true,
  createdAt: "2026-10-20T12:00:00Z",
  updatedAt: "2026-10-20T12:00:00Z",
};
const webhookRead = {
  id: "t1",
  automationId: "a1",
  kind: "webhook" as const,
  enabled: true,
  webhookUrl: "https://www.orazaka.com/edge/hooks/t1",
  secretHint: "ab12",
  catchUp: "once" as const,
  createdAt: "2026-10-20T12:00:00Z",
};

function renderSection() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AutomationsSection />
    </QueryClientProvider>,
  );
}

describe("AutomationsSection", () => {
  beforeEach(() => {
    studios.fetchCatalogue.mockResolvedValue([
      { studioKey: "compliance-check", label: "Compliance check", locked: false },
    ] as never);
    api.list.mockResolvedValue([automation]);
    api.triggers.mockResolvedValue([webhookRead]);
    api.runs.mockResolvedValue([]);
  });
  afterEach(() => jest.resetAllMocks());

  it("says so when there is no automation yet", async () => {
    api.list.mockResolvedValue([]);
    renderSection();
    expect(await screen.findByText(t.emptyTitle)).toBeInTheDocument();
  });

  it("lists an automation with the Studio it runs, by its label", async () => {
    renderSection();
    expect(await screen.findByText(automation.name)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Runs Compliance check")).toBeInTheDocument());
  });

  it("offers a retry when the list cannot be loaded", async () => {
    api.list.mockRejectedValueOnce(new Error("down"));
    renderSection();
    expect(await screen.findByText(t.loadError)).toBeInTheDocument();
    api.list.mockResolvedValue([automation]);
    fireEvent.click(screen.getByRole("button", { name: t.retry }));
    expect(await screen.findByText(automation.name)).toBeInTheDocument();
  });

  it("pauses an automation with its switch", async () => {
    api.update.mockResolvedValue({ ...automation, enabled: false });
    renderSection();
    await screen.findByText(automation.name);
    fireEvent.click(screen.getByRole("switch", { name: t.enabledLabel }));
    await waitFor(() => expect(api.update).toHaveBeenCalledWith("a1", { enabled: false }));
  });

  it("deletes only after the owner confirms", async () => {
    api.remove.mockResolvedValue(undefined);
    const confirm = jest.spyOn(window, "confirm");
    renderSection();
    await screen.findByText(automation.name);
    confirm.mockReturnValueOnce(false);
    fireEvent.click(screen.getByRole("button", { name: t.delete }));
    expect(api.remove).not.toHaveBeenCalled();
    confirm.mockReturnValueOnce(true);
    fireEvent.click(screen.getByRole("button", { name: t.delete }));
    await waitFor(() => expect(api.remove).toHaveBeenCalledWith("a1"));
    confirm.mockRestore();
  });

  it("shows the triggers of an opened automation, with the secret's hint and never a secret", async () => {
    renderSection();
    await screen.findByText(automation.name);
    fireEvent.click(screen.getByRole("button", { name: t.showDetails }));
    expect(await screen.findByText(webhookRead.webhookUrl)).toBeInTheDocument();
    expect(screen.getByText("Secret ending in ab12")).toBeInTheDocument();
    expect(screen.queryByText(/whsec_/)).toBeNull();
  });

  it("adds a webhook: the secret is shown once in a dialog, then gone", async () => {
    api.addTrigger.mockResolvedValue({ ...webhookRead, id: "t2", secret: ["whsec", "SHOWN-ONCE-ONLY"].join("_") });
    renderSection();
    await screen.findByText(automation.name);
    fireEvent.click(screen.getByRole("button", { name: t.showDetails }));
    fireEvent.click(await screen.findByRole("button", { name: t.triggers.addWebhook }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByTestId("webhook-secret")).toHaveTextContent(["whsec", "SHOWN-ONCE-ONLY"].join("_"));
    expect(within(dialog).getByTestId("webhook-url")).toHaveTextContent("/hooks/t1");
    expect(api.addTrigger).toHaveBeenCalledWith("a1", { kind: "webhook" });

    fireEvent.click(within(dialog).getByRole("button", { name: t.secret.done }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.queryByText(/SHOWN-ONCE-ONLY/)).toBeNull();
  });

  it("rotates a secret only after the owner confirms, and shows the new one once", async () => {
    api.rotateTrigger.mockResolvedValue({ ...webhookRead, secret: ["whsec", "ROTATED"].join("_") });
    const confirm = jest.spyOn(window, "confirm").mockReturnValue(true);
    renderSection();
    await screen.findByText(automation.name);
    fireEvent.click(screen.getByRole("button", { name: t.showDetails }));
    fireEvent.click(await screen.findByRole("button", { name: t.triggers.rotate }));
    expect(confirm).toHaveBeenCalledWith(t.triggers.rotateConfirm);
    expect(await screen.findByTestId("webhook-secret")).toHaveTextContent(["whsec", "ROTATED"].join("_"));
    confirm.mockRestore();
  });

  it("lists the runs of an automation with their state", async () => {
    api.runs.mockResolvedValue([
      {
        id: "r1",
        automationId: "a1",
        trigger: "webhook",
        status: "succeeded",
        startedAt: "2026-10-20T12:00:01Z",
        finishedAt: "2026-10-20T12:00:06Z",
        studioRunId: "5a3c0c1e-0000",
      },
      {
        id: "r2",
        automationId: "a1",
        trigger: "schedule",
        status: "failed",
        errorCode: "studio_refused_402",
        errorMessage: "the engine refused the run (402)",
        startedAt: "2026-10-20T11:00:00Z",
      },
    ]);
    renderSection();
    await screen.findByText(automation.name);
    fireEvent.click(screen.getByRole("button", { name: t.showDetails }));
    const runsTab = await screen.findByRole("tab", { name: t.tabs.runs });
    fireEvent.mouseDown(runsTab);
    fireEvent.click(runsTab);
    expect(await screen.findByText(translations.en.automations.runs.status.succeeded)).toBeInTheDocument();
    expect(screen.getByText("the engine refused the run (402)")).toBeInTheDocument();
    expect(screen.getByText("5 s")).toBeInTheDocument();
  });
});
