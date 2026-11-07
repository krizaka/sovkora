import { mediaSource } from "../assets";

describe("mediaSource", () => {
  it("resolves an edge path against the edge and sends the Bearer", () => {
    expect(mediaSource("/api/v1/assets/j1/video.mp4", "tok")).toEqual({
      uri: "https://www.orazaka.com/edge/api/v1/assets/j1/video.mp4",
      headers: { Authorization: "Bearer tok" },
    });
  });
  it("never sends the Bearer to an absolute URL", () => {
    expect(mediaSource("https://cdn.example/x.mp4", "tok")).toEqual({ uri: "https://cdn.example/x.mp4" });
  });
});
