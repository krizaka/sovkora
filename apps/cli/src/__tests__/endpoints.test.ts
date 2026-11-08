/**
 * @file endpoints.test.ts
 * @description The install wizard's Ollama answer: a full URL is kept (https stays https), a bare host
 * behaves as before (http on 11434), anything else is refused before it reaches an env file.
 */

import { ollamaBaseUrl } from "../utils/endpoints";

describe("ollamaBaseUrl", () => {
  it("keeps a bare host as plain http on the default port", () => {
    expect(ollamaBaseUrl("ollama-server.corp.internal")).toBe("http://ollama-server.corp.internal:11434");
    expect(ollamaBaseUrl("  10.0.0.7 ")).toBe("http://10.0.0.7:11434");
  });

  it("keeps an https URL as typed, without a trailing slash", () => {
    expect(ollamaBaseUrl("https://ollama.corp.internal:8443/")).toBe("https://ollama.corp.internal:8443");
  });

  it("keeps an explicit http URL", () => {
    expect(ollamaBaseUrl("http://ollama:11434")).toBe("http://ollama:11434");
  });

  it("refuses an empty answer, another scheme and a broken URL", () => {
    expect(() => ollamaBaseUrl("  ")).toThrow("empty");
    expect(() => ollamaBaseUrl("ftp://ollama")).toThrow("http or https");
    expect(() => ollamaBaseUrl("http://")).toThrow("not a valid URL");
  });
});
