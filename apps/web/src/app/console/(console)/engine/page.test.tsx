import { queryWrapper } from "@/core/testing/queryWrapper";
import { render, screen } from "@testing-library/react";
import { headers } from "next/headers";
import EnginePage from "./page";

jest.mock("next/headers", () => ({ headers: jest.fn() }));

describe("EnginePage", () => {
  it("selects the screen language from the request Accept-Language header", async () => {
    jest
      .mocked(headers)
      .mockResolvedValue(new Headers({ "accept-language": "fr-FR,fr;q=0.9" }) as Awaited<ReturnType<typeof headers>>);
    global.fetch = jest.fn(async () => ({ ok: true, json: async () => [] }) as Response) as unknown as typeof fetch;

    render(await EnginePage(), { wrapper: queryWrapper() });

    expect(await screen.findByRole("heading", { name: "Moteur" })).toBeTruthy();
  });
});
