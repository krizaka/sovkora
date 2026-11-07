// jsdom has no ResizeObserver; @krizaka/ui measures with it (the gliding light of Tabs, the charts' width).
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver;
