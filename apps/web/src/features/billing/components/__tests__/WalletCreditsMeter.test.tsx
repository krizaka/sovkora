import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { WalletCreditsMeter } from "@/features/billing/components/WalletCreditsMeter";
import { translations } from "@/core/context/translations";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ locale: "en", t: jest.requireActual("@/core/context/translations").translations.en }),
}));

const PLAN = { label: "Free", monthlyCreditGrant: 500 } as never;

function renderMeter(plan: never | null) {
  return render(<WalletCreditsMeter wallet={null} available={0} plan={plan} loading={false} href="/credits" />);
}

describe("WalletCreditsMeter with nothing left", () => {
  it("says « this period » when the plan grants credits each period (krizaka/orazaka#443)", () => {
    renderMeter(PLAN);
    expect(screen.getByText(translations.en.insights.usage.noCreditsLeft)).toBeInTheDocument();
    expect(screen.queryByText(translations.en.insights.usage.noCredits)).toBeNull();
  });

  it("keeps « No credits yet » when no plan grants any", () => {
    renderMeter(null);
    expect(screen.getByText(translations.en.insights.usage.noCredits)).toBeInTheDocument();
  });
});
