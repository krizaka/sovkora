import { act, renderHook, waitFor } from "@testing-library/react";
import { queryWrapper } from "@/core/testing/queryWrapper";
import { useLawLoad } from "@/console/features/law/hooks/useLawLoad";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => (resolve = r));
  return { promise, resolve };
}

describe("useLawLoad", () => {
  it("reads once, then again on demand", async () => {
    const load = jest.fn().mockResolvedValueOnce(["a"]).mockResolvedValueOnce(["a", "b"]);
    const { result } = renderHook(() => useLawLoad<string[]>(load, []), { wrapper: queryWrapper() });
    await waitFor(() => expect(result.current.data).toEqual(["a"]));
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.data).toEqual(["a", "b"]));
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("never shows a read that the next one overtook (A then B, A answers last)", async () => {
    const a = deferred<string>();
    const b = deferred<string>();
    const loadA = () => a.promise;
    const loadB = () => b.promise;
    const { result, rerender } = renderHook(({ load }) => useLawLoad<string>(load, ""), {
      wrapper: queryWrapper(),
      initialProps: { load: loadA },
    });
    rerender({ load: loadB });
    await act(async () => b.resolve("B"));
    await waitFor(() => expect(result.current.data).toBe("B"));
    await act(async () => a.resolve("A"));
    expect(result.current.data).toBe("B");
  });

  it("words a failed read as a refusal and keeps the initial data", async () => {
    const failing = () => Promise.reject(new Error("down"));
    const { result } = renderHook(() => useLawLoad<string[]>(failing, []), { wrapper: queryWrapper() });
    await waitFor(() => expect(result.current.refusal).not.toBeNull());
    expect(result.current.refusal?.status).toBe(503);
    expect(result.current.data).toEqual([]);
  });

  it("does not read without a load", () => {
    const { result } = renderHook(() => useLawLoad<string[]>(null, ["x"]), { wrapper: queryWrapper() });
    expect(result.current.isLoading).toBe(false);
    expect(result.current.data).toEqual(["x"]);
  });
});
