// Expo SDK 53's "winter" runtime installs lazy globals (import.meta registry, structuredClone) whose first read
// requires a module; under Jest that read can land after the test file is torn down ("require a file outside of the
// scope of the test code"). Define them eagerly here.
jest.mock("expo/src/winter/ImportMetaRegistry", () => ({
  ImportMetaRegistry: {
    get url() {
      return null;
    },
  },
}));
if (typeof globalThis.structuredClone !== "function") {
  // The polyfill itself: structuredClone is what is missing here.
  globalThis.structuredClone = (value) => JSON.parse(JSON.stringify(value)); // NOSONAR
}

// expo-video is a native module (a player has no JS to run under Jest): screens render a plain View in its place.
jest.mock("expo-video", () => {
  const { View } = jest.requireActual("react-native");
  const player = { loop: false, play: () => undefined };
  return { useVideoPlayer: () => player, VideoView: (props) => jest.requireActual("react").createElement(View, props) };
});

// krizaka-internal#2684: the screens draw the commercial mark; the code-named one must never appear. Both are marked so a test can tell.
jest.mock("@krizaka/ui/native", () => {
  const { View } = jest.requireActual("react-native");
  const actual = jest.requireActual("@krizaka/ui/native");
  return {
    ...actual,
    SovkoraMark: () => jest.requireActual("react").createElement(View, { testID: "sovkora-mark" }),
    OrazakaMark: () => jest.requireActual("react").createElement(View, { testID: "orazaka-mark" }),
  };
});
