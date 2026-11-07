import React from "react";
import { render } from "@testing-library/react-native";
import { AppState } from "react-native";
import { KEEP_ALIVE_MS, useKeepAlive } from "../keepAlive";
import { answerFetch } from "../../__tests__/test-api";

function Probe({ token }: Readonly<{ token: string | null }>): null {
  useKeepAlive(token);
  return null;
}

const setState = (value: string): void => {
  Object.defineProperty(AppState, "currentState", { value, configurable: true });
};

describe("the connection stays warm while the app is open", () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it("pings the edge well inside the proxy's idle limit, with the bearer", async () => {
    const calls = answerFetch({ "/api/v1/profile": {} });
    setState("active");
    await render(<Probe token="jwt" />);

    jest.advanceTimersByTime(KEEP_ALIVE_MS * 2 + 10);

    expect(KEEP_ALIVE_MS).toBeLessThan(30_000);
    expect(calls.filter((c) => c.path === "/api/v1/profile")).toHaveLength(2);
    expect(calls[0]?.headers.Authorization).toBe("Bearer jwt");
  });

  it("does nothing signed out", async () => {
    const calls = answerFetch({ "/api/v1/profile": {} });
    await render(<Probe token={null} />);
    jest.advanceTimersByTime(KEEP_ALIVE_MS * 3);
    expect(calls).toHaveLength(0);
  });

  it("does nothing in the background", async () => {
    const calls = answerFetch({ "/api/v1/profile": {} });
    setState("background");
    await render(<Probe token="jwt" />);
    jest.advanceTimersByTime(KEEP_ALIVE_MS * 3);
    expect(calls).toHaveLength(0);
  });
});
