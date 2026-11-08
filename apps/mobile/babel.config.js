// Expo's Babel preset — what Metro uses by default, written down so Jest's `screens` project transforms the same way.
module.exports = function babelConfig(api) {
  api.cache(true);
  return { presets: ["babel-preset-expo"] };
};
