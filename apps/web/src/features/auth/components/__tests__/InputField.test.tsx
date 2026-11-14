import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { z } from "zod";
import { createKrizakaForm } from "@krizaka/ui/form";
import { InputField } from "@/features/auth/components/InputField";

const sample = createKrizakaForm({
  schema: z.object({ email: z.string().min(1, "sample.email") }),
  defaultValues: { email: "" },
});

function Sample({ hint }: Readonly<{ hint?: string }>) {
  const form = sample.useForm({ i18n: { t: () => "Email is required" } });
  return (
    <form.Form>
      <InputField name="email" id="sample-email" label="Email" hint={hint} />
      <form.Submit>Go</form.Submit>
    </form.Form>
  );
}

describe("InputField", () => {
  it("keeps the id the caller gave and names the control by its label", () => {
    render(<Sample />);
    expect(screen.getByLabelText("Email")).toHaveAttribute("id", "sample-email");
  });

  it("shows the value typed", () => {
    render(<Sample />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "a@b.com" } });
    expect(screen.getByLabelText("Email")).toHaveValue("a@b.com");
  });

  it("says what is wrong and keeps the hint beside it", async () => {
    render(<Sample hint="We never share it" />);
    fireEvent.click(screen.getByRole("button", { name: "Go" }));
    expect(await screen.findByText("Email is required")).toBeInTheDocument();
    expect(screen.getByText("We never share it")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInvalid();
  });
});
