import "@testing-library/jest-dom";
import { fireEvent, render as plainRender, screen, waitFor } from "@testing-library/react";
import { queryWrapper } from "@/core/testing/queryWrapper";
import type { Installation } from "@krizaka/orazaka-shared";
import { ConsentNotice } from "@/features/studio/consent/ConsentNotice";
import { StudioApi } from "@/services/studio.api";

/** These screens read server state: every render gets a fresh query client. */
const render = (ui: Parameters<typeof plainRender>[0], options?: Parameters<typeof plainRender>[1]) =>
  plainRender(ui, { wrapper: queryWrapper(), ...options });

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));
jest.mock("@/services/studio.api", () => ({
  StudioApi: { fetchPacks: jest.fn(), uninstall: jest.fn(), eraseWellbeingData: jest.fn() },
}));

const pack = (regulatoryClass: string) =>
  [
    { packKey: "bien-etre", regulatoryClass, studioKeys: ["guided-journaling"] },
    { packKey: "other", regulatoryClass: "REGULATED", studioKeys: ["not-this-one"] },
  ] as never;
const installation = { id: "inst-1", status: "ACTIVE" } as Installation;

describe("ConsentNotice (before the click, and the way out)", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    sessionStorage.clear();
  });

  it("says before the click that a REGULATED Studio asks for consent", async () => {
    jest.mocked(StudioApi.fetchPacks).mockResolvedValue(pack("REGULATED"));
    render(<ConsentNotice studioKey="guided-journaling" installation={undefined} onChanged={jest.fn()} />);
    expect(await screen.findByText(/Consent asked/)).toBeInTheDocument();
  });

  it("says nothing for a Studio that asks no consent", async () => {
    jest.mocked(StudioApi.fetchPacks).mockResolvedValue(pack("STANDARD"));
    const { container } = render(
      <ConsentNotice studioKey="guided-journaling" installation={undefined} onChanged={jest.fn()} />,
    );
    await waitFor(() => expect(StudioApi.fetchPacks).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing for a Studio of no regulated pack, even if another pack is regulated", async () => {
    jest
      .mocked(StudioApi.fetchPacks)
      .mockResolvedValue([{ packKey: "other", regulatoryClass: "REGULATED", studioKeys: ["not-this-one"] }] as never);
    const { container } = render(
      <ConsentNotice studioKey="guided-journaling" installation={undefined} onChanged={jest.fn()} />,
    );
    await waitFor(() => expect(StudioApi.fetchPacks).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing when the pack is not known or the catalogue does not answer", async () => {
    jest.mocked(StudioApi.fetchPacks).mockRejectedValue(new Error("down"));
    const { container } = render(
      <ConsentNotice studioKey="guided-journaling" installation={installation} onChanged={jest.fn()} />,
    );
    await waitFor(() => expect(StudioApi.fetchPacks).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("lets the person withdraw: a confirmation, then the installation is revoked", async () => {
    jest.mocked(StudioApi.fetchPacks).mockResolvedValue(pack("REGULATED"));
    jest.mocked(StudioApi.uninstall).mockResolvedValue(undefined);
    const onChanged = jest.fn();
    render(<ConsentNotice studioKey="guided-journaling" installation={installation} onChanged={onChanged} />);

    fireEvent.click(await screen.findByRole("button", { name: "Withdraw my consent" }));
    expect(screen.getByRole("dialog", { name: "Withdraw your consent?" })).toBeInTheDocument();
    expect(StudioApi.uninstall).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Withdraw consent" }));

    await waitFor(() => expect(StudioApi.uninstall).toHaveBeenCalledWith("inst-1"));
    expect(onChanged).toHaveBeenCalled();
  });

  it("keeps the consent when the person changes their mind", async () => {
    jest.mocked(StudioApi.fetchPacks).mockResolvedValue(pack("REGULATED"));
    render(<ConsentNotice studioKey="guided-journaling" installation={installation} onChanged={jest.fn()} />);
    fireEvent.click(await screen.findByRole("button", { name: "Withdraw my consent" }));
    fireEvent.click(screen.getByRole("button", { name: "Keep it" }));
    expect(StudioApi.uninstall).not.toHaveBeenCalled();
  });

  it("deletes all the wellbeing data after a confirmation: the pack is erased, nothing before the click", async () => {
    jest.mocked(StudioApi.fetchPacks).mockResolvedValue(pack("REGULATED"));
    jest
      .mocked(StudioApi.eraseWellbeingData)
      .mockResolvedValue({ packKey: "bien-etre", entries: 2, installations: 1, jobs: 2 });
    const onChanged = jest.fn();
    render(<ConsentNotice studioKey="guided-journaling" installation={installation} onChanged={onChanged} />);

    fireEvent.click(await screen.findByRole("button", { name: "Delete all my wellbeing data" }));
    expect(screen.getByRole("dialog", { name: "Delete all your wellbeing data?" })).toBeInTheDocument();
    expect(StudioApi.eraseWellbeingData).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Delete everything" }));

    await waitFor(() => expect(StudioApi.eraseWellbeingData).toHaveBeenCalledWith("bien-etre"));
    expect(onChanged).toHaveBeenCalled();
    expect(await screen.findByText("Your wellbeing data has been deleted.")).toBeInTheDocument();
  });

  it("keeps everything when the person backs out of the deletion", async () => {
    jest.mocked(StudioApi.fetchPacks).mockResolvedValue(pack("REGULATED"));
    render(<ConsentNotice studioKey="guided-journaling" installation={installation} onChanged={jest.fn()} />);
    fireEvent.click(await screen.findByRole("button", { name: "Delete all my wellbeing data" }));
    fireEvent.click(screen.getByRole("button", { name: "Keep it" }));
    expect(StudioApi.eraseWellbeingData).not.toHaveBeenCalled();
  });

  it("says so when the deletion fails, and claims nothing was deleted", async () => {
    jest.mocked(StudioApi.fetchPacks).mockResolvedValue(pack("REGULATED"));
    jest.mocked(StudioApi.eraseWellbeingData).mockRejectedValue(new Error("Nothing was deleted; please ask again"));
    render(<ConsentNotice studioKey="guided-journaling" installation={installation} onChanged={jest.fn()} />);
    fireEvent.click(await screen.findByRole("button", { name: "Delete all my wellbeing data" }));
    fireEvent.click(screen.getByRole("button", { name: "Delete everything" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Nothing was deleted");
    expect(screen.queryByText("Your wellbeing data has been deleted.")).not.toBeInTheDocument();
  });
});
