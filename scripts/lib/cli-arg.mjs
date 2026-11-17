// The one reader of a `--name value` flag for the workspace scripts (gpu-smoke, gpu-i2v-run, journey-dev, runpod-batch,
// showcase-image-run): written once here instead of in each script.

/** The value after `--name` in `argv`, or `fallback` when the flag is absent (a flag in first position is not a flag). */
export function arg(name, fallback, argv = process.argv) {
  const i = argv.indexOf(`--${name}`);
  return i > 0 ? argv[i + 1] : fallback;
}
