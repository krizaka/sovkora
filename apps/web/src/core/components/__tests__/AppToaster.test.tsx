import "@testing-library/jest-dom";
import { act, render, screen } from "@testing-library/react";
import { toast } from "@krizaka/ui/toast";
import { AppToaster } from "@/core/components/AppToaster";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: { notifications: { region: "Notifications", dismiss: "Dismiss notification" } } }),
}));

describe("AppToaster (@krizaka/ui Toaster)", () => {
  it("names its region and shows what toast() announces, with a named close button", async () => {
    render(<AppToaster />);
    expect(screen.getByLabelText(/Notifications/)).toBeInTheDocument();
    act(() => {
      toast.success("Chat created");
    });
    expect(await screen.findByText("Chat created")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Dismiss notification" })).toBeInTheDocument();
  });
});
