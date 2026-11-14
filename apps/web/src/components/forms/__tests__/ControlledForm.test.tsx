import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ControlledForm } from "@/components/forms/ControlledForm";

describe("ControlledForm", () => {
  it("runs onSubmit when its button is pressed", async () => {
    const onSubmit = jest.fn();
    render(
      <ControlledForm onSubmit={onSubmit}>
        <button type="submit">Save</button>
      </ControlledForm>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  });

  it("runs onSubmit for a button outside the form that names it", async () => {
    const onSubmit = jest.fn();
    render(
      <>
        <ControlledForm id="outer" onSubmit={onSubmit}>
          <p>fields</p>
        </ControlledForm>
        <button type="submit" form="outer">
          Send
        </button>
      </>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Send" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  });
});
