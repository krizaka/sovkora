/**
 * The one place the page reads the time from: the components pass it to the pure functions of `usageStats`
 * (`now: Date` is a parameter there, so they are testable). Same instant as `new Date()`.
 */
export const systemNow = (): Date => new Date(performance.timeOrigin + performance.now());
