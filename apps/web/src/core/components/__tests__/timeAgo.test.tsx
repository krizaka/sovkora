import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { enUS, fr } from "date-fns/locale";
import { timeAgoCell } from "@/core/components/timeAgo";

describe("timeAgoCell", () => {
  const iso = new Date(Date.now() - 3 * 3600_000).toISOString();

  it("says how long ago in the locale, with the exact moment as its title", () => {
    render(<>{timeAgoCell(iso, enUS)}</>);
    const el = screen.getByText(/about 3 hours ago/);
    expect(el.tagName).toBe("TIME");
    expect(el).toHaveAttribute("datetime", iso);
    expect(el).toHaveAttribute("title", iso);
  });

  it("follows the locale", () => {
    render(<>{timeAgoCell(iso, fr)}</>);
    expect(screen.getByText(/il y a environ 3 heures/)).toBeInTheDocument();
  });
});
