import "@testing-library/jest-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { InterceptionForm } from "@/features/auth/components/InterceptionForm";

const mockPush = jest.fn();
const mockUpdate = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

jest.mock("next-auth/react", () => ({
  useSession: () => ({
    data: { user: { id: "u1" } },
    update: mockUpdate,
  }),
}));

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      interception: {
        submitPreferences: "Save Preferences",
        resolving: "Saving...",
        fieldRequired: (name: string) => `${name} is required`,
        loadingSchema: "Loading schema...",
        loadingError: "Error",
        loadingErrorDesc: "Could not load",
        retry: "Retry",
      },
    },
    locale: "en",
  }),
}));

jest.mock("@/features/auth/hooks/useInterception", () => ({
  useInterceptionSchema: () => ({
    schema: {
      id: "onboarding",
      title: "Complete Your Profile",
      description: "Fill in the required fields",
      fields: [{ name: "fullName", label: "Full Name", type: "text", required: true, defaultValue: "" }],
    },
    isLoading: false,
    error: null,
  }),
  useResolveInterception: () => ({
    resolve: jest.fn(),
    isPending: false,
  }),
}));

jest.mock("@/features/auth/components/InterceptionFormField", () => ({
  InterceptionFormField: ({ field }: { field: { label: string } }) => (
    <div data-testid={`field-${field.label}`}>{field.label}</div>
  ),
}));

jest.mock("@krizaka/orazaka-design-system", () => ({
  ...jest.requireActual("@krizaka/orazaka-design-system"),
  Button: ({ children, ...props }: { children: React.ReactNode } & React.ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>{children}</button>
  ),
  Card: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  CardContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  CardDescription: ({ children }: { children: React.ReactNode }) => <p>{children}</p>,
  CardHeader: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  CardTitle: ({ children }: { children: React.ReactNode }) => <h3>{children}</h3>,
  CardFooter: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

describe("InterceptionForm", () => {
  it("renders schema title and submit button", async () => {
    render(<InterceptionForm schemaId="onboarding" />);
    await waitFor(() => {
      expect(screen.getByText("Complete Your Profile")).toBeInTheDocument();
    });
    expect(screen.getByText("Save Preferences")).toBeInTheDocument();
    expect(screen.getByText("Full Name")).toBeInTheDocument();
  });
});
