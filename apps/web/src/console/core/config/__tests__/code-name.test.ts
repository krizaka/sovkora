/** @jest-environment node */
import { consoleCodeName } from "@/console/core/config/code-name";

describe("the console's code name line", () => {
  it("is shown on a development or staging console, under the commercial name", () => {
    expect(consoleCodeName("dev")).toBe("code name: orazaka");
    expect(consoleCodeName("staging")).toBe("code name: orazaka");
  });

  it("is never shown on the production console", () => {
    expect(consoleCodeName("production")).toBeNull();
  });
});
