import { afterEach, expect, it, vi } from "vitest";
afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.useRealTimers();
  vi.resetModules();
});
async function adapter() {
  vi.stubEnv("VITE_LEGACY_BASE", "https://media.example.test/context/");
  vi.stubGlobal("window", { location: { origin: "https://app.example.test" } });
  return import("../src/api/legacy");
}
it("resolves old public resources through Boot and keeps unsafe schemes blocked", async () => {
  const { coverUrl } = await adapter();
  expect(coverUrl("/static/cover.png")).toBe(
    "https://app.example.test/api/v1/assets/static/cover.png",
  );
  expect(coverUrl("javascript:alert(1)")).toBeUndefined();
});
it("waits through queued/running longer than the former 30 seconds and carries auth", async () => {
  vi.useFakeTimers();
  const { prepareLegacy } = await adapter();
  let calls = 0;
  const fetcher = vi.fn(async () =>
    Response.json(
      ++calls === 1
        ? { success: true }
        : calls < 25
          ? { state: "QUEUED", ready: false }
          : { state: "READY", ready: true },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  const status = vi.fn();
  const source = prepareLegacy(
    "9223372036854775807",
    "test-token",
    new AbortController().signal,
    status,
  );
  await vi.advanceTimersByTimeAsync(40000);
  expect(await source).toMatchObject({ hls: true, token: "test-token" });
  expect((await source).url).toContain("songId=9223372036854775807");
  expect((await source).url).toContain("/api/v1/media/hls?");
  expect(fetcher.mock.calls[0]?.length).toBe(2);
  expect(status).toHaveBeenCalledWith("媒体正在排队…");
});
it("fails immediately for FAILED instead of pretending ready", async () => {
  const { prepareLegacy } = await adapter();
  let calls = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      Response.json(
        ++calls === 1 ? { success: true } : { state: "FAILED", ready: false },
      ),
    ),
  );
  await expect(
    prepareLegacy("1", "token", new AbortController().signal, vi.fn()),
  ).rejects.toThrow("转换失败");
});

it("bounds queued preparation at five minutes and allows an explicit retry", async () => {
  vi.useFakeTimers();
  const { prepareLegacy } = await adapter();
  let calls = 0;
  const fetcher = vi.fn(async () =>
    Response.json(
      ++calls === 1 ? { success: true } : { state: "QUEUED", ready: false },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  const preparation = expect(
    prepareLegacy("1", "token", new AbortController().signal, vi.fn()),
  ).rejects.toThrow("超过五分钟");
  await vi.advanceTimersByTimeAsync(300000);
  await preparation;
  const stoppedAt = fetcher.mock.calls.length;
  await vi.advanceTimersByTimeAsync(5000);
  expect(fetcher).toHaveBeenCalledTimes(stoppedAt);

  let retryCalls = 0;
  vi.stubGlobal("fetch", vi.fn(async () => Response.json(
    ++retryCalls === 1 ? { success: true } : { state: "READY", ready: true },
  )));
  await expect(
    prepareLegacy("1", "token", new AbortController().signal, vi.fn()),
  ).resolves.toMatchObject({ hls: true });
});

it("cancels polling without another status request or late ready result", async () => {
  vi.useFakeTimers();
  const { prepareLegacy } = await adapter();
  let calls = 0;
  const fetcher = vi.fn(async () => Response.json(
    ++calls === 1 ? { success: true } : { state: "QUEUED", ready: false },
  ));
  vi.stubGlobal("fetch", fetcher);
  const controller = new AbortController();
  const preparation = expect(
    prepareLegacy("1", "token", controller.signal, vi.fn()),
  ).rejects.toThrow("cancel preparation");
  await vi.advanceTimersByTimeAsync(100);
  expect(fetcher).toHaveBeenCalledTimes(2);
  controller.abort(new Error("cancel preparation"));
  await preparation;
  await vi.advanceTimersByTimeAsync(5000);
  expect(fetcher).toHaveBeenCalledTimes(2);
});
