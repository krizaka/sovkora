import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { Installation, StudioDetail } from "@krizaka/orazaka-shared";
import { StudioRunPanel } from "@/features/studio/components/StudioRunPanel";
import { StudioApi } from "@/services/studio.api";
import { ConsentRequiredError } from "@/features/studio/consent/consent";

const push = jest.fn();
jest.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));
jest.mock("@/features/studio/components/RunForm", () => ({
  RunForm: ({ onSubmit }: { onSubmit: (inputs: Record<string, unknown>) => void }) => (
    <button type="button" onClick={() => onSubmit({ prompt: "a lamp" })}>
      Run
    </button>
  ),
}));
jest.mock("@/services/studio.api", () => ({
  StudioApi: { install: jest.fn(), startRun: jest.fn(), startStudioRun: jest.fn() },
}));

const run = { id: "run-1" };
const detail = (kind: StudioDetail["kind"]): StudioDetail => ({
  studioKey: "mood-tracking",
  label: "Mood tracking",
  tagline: null,
  description: null,
  profession: "general",
  iconKey: "heart",
  heroAssetId: null,
  pricing: "FREE",
  kind,
  status: "PUBLISHED",
  latestVersion: "1.2.0",
  estimatedCredits: 2,
  inputSchema: null,
  configSchema: null,
  locked: false,
  lockedReason: "NONE",
  packKey: null,
});

describe("StudioRunPanel", () => {
  beforeEach(() => jest.clearAllMocks());

  it("installs a VERTICAL Studio on its first run, then runs it: there is no Install step", async () => {
    jest.mocked(StudioApi.install).mockResolvedValue({ id: "inst-1" } as Installation);
    jest.mocked(StudioApi.startRun).mockResolvedValue(run as never);
    render(<StudioRunPanel studio={detail("VERTICAL")} />);

    fireEvent.click(screen.getByRole("button", { name: "Run" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/studios/runs/run-1"));
    expect(StudioApi.install).toHaveBeenCalledWith("mood-tracking", {}, undefined, "en");
    expect(StudioApi.startRun).toHaveBeenCalledWith("inst-1", { prompt: "a lamp" });
  });

  it("runs an installed Studio through its installation, without installing again", async () => {
    jest.mocked(StudioApi.startRun).mockResolvedValue(run as never);
    render(
      <StudioRunPanel studio={detail("VERTICAL")} installation={{ id: "inst-9", status: "ACTIVE" } as Installation} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Run" }));

    await waitFor(() => expect(StudioApi.startRun).toHaveBeenCalledWith("inst-9", { prompt: "a lamp" }));
    expect(StudioApi.install).not.toHaveBeenCalled();
  });

  it("runs a TOOLKIT Studio by its key: it never has an installation (ADR-061)", async () => {
    jest.mocked(StudioApi.startStudioRun).mockResolvedValue(run as never);
    render(<StudioRunPanel studio={detail("TOOLKIT")} />);

    fireEvent.click(screen.getByRole("button", { name: "Run" }));

    await waitFor(() => expect(StudioApi.startStudioRun).toHaveBeenCalledWith("mood-tracking", { prompt: "a lamp" }));
    expect(StudioApi.install).not.toHaveBeenCalled();
  });

  describe("a wellbeing Studio (the server answers 451 consent_required)", () => {
    const statement = "Ce Studio enregistre ce que vous y écrivez. Vous consentez explicitement.";
    const refused = () => new ConsentRequiredError({ version: "1.0", statement });

    it("cannot run without consent: the 451 becomes the consent step, no run starts", async () => {
      jest.mocked(StudioApi.install).mockRejectedValue(refused());
      render(<StudioRunPanel studio={detail("VERTICAL")} />);

      fireEvent.click(screen.getByRole("button", { name: "Run" }));

      expect(await screen.findByRole("dialog", { name: /Before you start Mood tracking/ })).toBeInTheDocument();
      expect(screen.getByText(statement)).toBeInTheDocument();
      expect(StudioApi.startRun).not.toHaveBeenCalled();
      expect(push).not.toHaveBeenCalled();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("installs with the three declarations, then runs the inputs the person had typed", async () => {
      jest
        .mocked(StudioApi.install)
        .mockRejectedValueOnce(refused())
        .mockResolvedValueOnce({ id: "inst-2" } as Installation);
      jest.mocked(StudioApi.startRun).mockResolvedValue(run as never);
      render(<StudioRunPanel studio={detail("VERTICAL")} />);
      fireEvent.click(screen.getByRole("button", { name: "Run" }));
      await screen.findByRole("dialog");

      fireEvent.click(screen.getByRole("checkbox", { name: /legal age/i }));
      fireEvent.click(screen.getByRole("checkbox", { name: /explicitly consent/i }));
      fireEvent.change(screen.getByRole("combobox"), { target: { value: "CA-QC" } });
      fireEvent.click(screen.getByRole("button", { name: "Agree and continue" }));

      await waitFor(() => expect(push).toHaveBeenCalledWith("/studios/runs/run-1"));
      expect(StudioApi.install).toHaveBeenLastCalledWith(
        "mood-tracking",
        {},
        { consentVersion: "1.0", ageAttested: true, region: "CA-QC" },
        "en",
      );
      expect(StudioApi.startRun).toHaveBeenCalledWith("inst-2", { prompt: "a lamp" });
    });

    it("declining starts nothing and closes the step", async () => {
      jest.mocked(StudioApi.install).mockRejectedValue(refused());
      render(<StudioRunPanel studio={detail("VERTICAL")} />);
      fireEvent.click(screen.getByRole("button", { name: "Run" }));
      await screen.findByRole("dialog");

      fireEvent.click(screen.getByRole("button", { name: "Not now" }));

      await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
      expect(StudioApi.startRun).not.toHaveBeenCalled();
    });

    it("keeps the step open and says why when the server refuses the age or the region", async () => {
      jest
        .mocked(StudioApi.install)
        .mockRejectedValueOnce(refused())
        .mockRejectedValueOnce(new Error("installing mood-tracking is not available in region 'CA'"));
      render(<StudioRunPanel studio={detail("VERTICAL")} />);
      fireEvent.click(screen.getByRole("button", { name: "Run" }));
      await screen.findByRole("dialog");
      fireEvent.click(screen.getByRole("checkbox", { name: /legal age/i }));
      fireEvent.click(screen.getByRole("checkbox", { name: /explicitly consent/i }));
      fireEvent.change(screen.getByRole("combobox"), { target: { value: "CA" } });
      fireEvent.click(screen.getByRole("button", { name: "Agree and continue" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("not available in region 'CA'");
      expect(screen.getByRole("dialog")).toBeInTheDocument();
      expect(StudioApi.startRun).not.toHaveBeenCalled();
    });

    it("asks again when a run of an installed Studio meets a statement that changed", async () => {
      jest
        .mocked(StudioApi.startRun)
        .mockRejectedValueOnce(new ConsentRequiredError({ version: "1.1", statement: "Nouvelle déclaration." }))
        .mockResolvedValueOnce(run as never);
      jest.mocked(StudioApi.install).mockResolvedValue({ id: "inst-9" } as Installation);
      render(
        <StudioRunPanel
          studio={detail("VERTICAL")}
          installation={{ id: "inst-9", status: "ACTIVE", config: {} } as Installation}
        />,
      );
      fireEvent.click(screen.getByRole("button", { name: "Run" }));
      expect(await screen.findByText("Nouvelle déclaration.")).toBeInTheDocument();

      fireEvent.click(screen.getByRole("checkbox", { name: /legal age/i }));
      fireEvent.click(screen.getByRole("checkbox", { name: /explicitly consent/i }));
      fireEvent.change(screen.getByRole("combobox"), { target: { value: "FR" } });
      fireEvent.click(screen.getByRole("button", { name: "Agree and continue" }));

      await waitFor(() => expect(push).toHaveBeenCalledWith("/studios/runs/run-1"));
      expect(StudioApi.install).toHaveBeenCalledWith(
        "mood-tracking",
        {},
        { consentVersion: "1.1", ageAttested: true, region: "FR" },
        "en",
      );
    });
  });
});
