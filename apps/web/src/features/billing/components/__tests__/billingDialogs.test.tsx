import "@testing-library/jest-dom";
import { fireEvent, render as plainRender, screen, waitFor } from "@testing-library/react";
import { queryWrapper } from "@/core/testing/queryWrapper";
import { translations } from "@/core/context/translations";
import { CostConfirmDialog } from "@/features/billing/components/CostConfirmDialog";
import { TopUpDialog } from "@/features/billing/components/TopUpDialog";
import { LowBalanceBanner } from "@/features/billing/components/LowBalanceBanner";

/** These screens read server state: every render gets a fresh query client. */
const render = (ui: Parameters<typeof plainRender>[0], options?: Parameters<typeof plainRender>[1]) =>
  plainRender(ui, { wrapper: queryWrapper(), ...options });

const t = translations.en;

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

let mockEstimate: { estimateCredits: number; availableCredits: number; affordable: boolean } | null = null;
let mockEstimateLoading = false;
jest.mock("@/features/billing/hooks/useCostEstimate", () => ({
  useCostEstimate: () => ({ estimate: mockEstimate, isLoading: mockEstimateLoading }),
}));

let mockWallet = { available: 12, isLow: true };
jest.mock("@/features/billing/hooks/useWallet", () => ({ useWallet: () => mockWallet }));

const mockFetchPlans = jest.fn();
jest.mock("@/services/billing.api", () => ({ BillingApi: { fetchPlans: () => mockFetchPlans() } }));

describe("CostConfirmDialog (@krizaka/ui Dialog)", () => {
  beforeEach(() => {
    mockEstimate = { estimateCredits: 40, availableCredits: 100, affordable: true };
    mockEstimateLoading = false;
  });

  const renderDialog = (props: Partial<React.ComponentProps<typeof CostConfirmDialog>> = {}) => {
    const onConfirm = jest.fn();
    const onCancel = jest.fn();
    render(
      <CostConfirmDialog
        open
        capability="image"
        actionLabel="Generate"
        onConfirm={onConfirm}
        onCancel={onCancel}
        {...props}
      />,
    );
    return { onConfirm, onCancel };
  };

  it("is a dialog named by the action, with the estimate", () => {
    renderDialog();
    expect(screen.getByRole("dialog", { name: "Generate" })).toBeInTheDocument();
    expect(screen.getByText(/40/)).toBeInTheDocument();
  });

  it("confirms and cancels", () => {
    const { onConfirm, onCancel } = renderDialog();
    fireEvent.click(screen.getByRole("button", { name: t.billing.confirm }));
    expect(onConfirm).toHaveBeenCalled();
    fireEvent.click(screen.getAllByRole("button", { name: t.billing.cancel })[0]);
    expect(onCancel).toHaveBeenCalled();
  });

  it("refuses to confirm an unaffordable action", () => {
    mockEstimate = { estimateCredits: 400, availableCredits: 100, affordable: false };
    renderDialog();
    expect(screen.getByText(t.billing.insufficient)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: t.billing.confirm })).toBeDisabled();
  });

  it("still confirms without an estimate", () => {
    mockEstimate = null;
    renderDialog();
    expect(screen.getByText(t.billing.noEstimate)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: t.billing.confirm })).toBeEnabled();
  });

  it("closes on Escape as a cancel", () => {
    const { onCancel } = renderDialog();
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(onCancel).toHaveBeenCalled();
  });

  it("renders nothing when closed", () => {
    renderDialog({ open: false });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("TopUpDialog (@krizaka/ui Dialog)", () => {
  it("lists the public, active plans", async () => {
    mockFetchPlans.mockResolvedValue([
      {
        planKey: "pro",
        label: "Pro",
        isPublic: true,
        isActive: true,
        monthlyCreditGrant: 1000,
        priceCents: 2900,
        currency: "CAD",
      },
      {
        planKey: "hidden",
        label: "Hidden",
        isPublic: false,
        isActive: true,
        monthlyCreditGrant: 1,
        priceCents: 1,
        currency: "CAD",
      },
    ]);
    render(<TopUpDialog open onClose={jest.fn()} />);
    expect(screen.getByRole("dialog", { name: t.billing.topUpTitle })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Pro")).toBeInTheDocument());
    // Canadian dollars, named for an international reader (money.constants.ts).
    expect(screen.getByText("CA$29.00")).toBeInTheDocument();
    expect(screen.queryByText("Hidden")).not.toBeInTheDocument();
  });

  it("says when there is no plan, and closes", async () => {
    mockFetchPlans.mockRejectedValue(new Error("down"));
    const onClose = jest.fn();
    render(<TopUpDialog open onClose={onClose} />);
    await waitFor(() => expect(screen.getByText(t.billing.noPlans)).toBeInTheDocument());
    fireEvent.click(screen.getAllByRole("button", { name: t.billing.close })[0]);
    expect(onClose).toHaveBeenCalled();
  });
});

describe("LowBalanceBanner (@krizaka/ui Alert)", () => {
  beforeEach(() => {
    mockWallet = { available: 12, isLow: true };
  });

  it("warns at once (role alert) with the balance", () => {
    render(<LowBalanceBanner onTopUp={jest.fn()} />);
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent(t.billing.lowBalanceTitle);
    expect(alert).toHaveTextContent("12");
  });

  it("offers the top-up and can be hidden for the session", () => {
    const onTopUp = jest.fn();
    render(<LowBalanceBanner onTopUp={onTopUp} />);
    fireEvent.click(screen.getByRole("button", { name: t.billing.topUp }));
    expect(onTopUp).toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: t.billing.dismiss }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("stays silent while the balance is not low", () => {
    mockWallet = { available: 900, isLow: false };
    render(<LowBalanceBanner />);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
