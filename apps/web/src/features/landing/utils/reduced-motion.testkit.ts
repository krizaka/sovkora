/** jsdom has no matchMedia: this one answers « reduce » for the reduced-motion query and « no » for the rest. */
export function mockReducedMotion(): void {
  Object.defineProperty(globalThis, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: query.includes("reduce"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      onchange: null,
      dispatchEvent: () => false,
    }),
  });
}
