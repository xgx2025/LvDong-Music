import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { apiRequest, jsonRequest, RequestError } from "../src/api/http";
import { usePlayerStore } from "../src/stores/player";
import { useSessionStore } from "../src/stores/session";
import type { Song } from "../src/types";

class FakeAudio extends EventTarget {
  preload = "";
  volume = 1;
  currentTime = 0;
  duration = 60;
  src = "";
  play = vi.fn(async () => {
    this.dispatchEvent(new Event("playing"));
  });
  pause = vi.fn(() => this.dispatchEvent(new Event("pause")));
  load = vi.fn();
  removeAttribute() {
    this.src = "";
  }
}
const song = (id: string) =>
  ({ songId: id, songName: `Song ${id}`, singers: [] }) as unknown as Song;
beforeEach(() => {
  setActivePinia(createPinia());
  vi.stubGlobal("Audio", FakeAudio);
});
afterEach(() => vi.unstubAllGlobals());
describe("request boundaries", () => {
  it("keeps string IDs and unwraps the project envelope", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          code: 0,
          message: "成功",
          data: { id: "9223372036854775807" },
        }),
      ),
    );
    expect(await apiRequest("/search")).toEqual({ id: "9223372036854775807" });
  });
  it("distinguishes business, HTTP, format and network failures", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ code: 400, message: "关键词太长" })),
    );
    await expect(apiRequest("/")).rejects.toMatchObject({
      kind: "business",
      message: "关键词太长",
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("", { status: 500 })),
    );
    await expect(apiRequest("/")).rejects.toMatchObject({
      kind: "http",
      status: 500,
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("html")),
    );
    await expect(jsonRequest("/")).rejects.toMatchObject({ kind: "format" });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("offline");
      }),
    );
    await expect(apiRequest("/")).rejects.toBeInstanceOf(RequestError);
    await expect(apiRequest("/")).rejects.toMatchObject({ kind: "network" });
  });
  it("preserves explicit abort cancellation", async () => {
    const controller = new AbortController();
    controller.abort();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw controller.signal.reason;
      }),
    );
    await expect(apiRequest("/", controller.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
  it("shows bounded business explanations for actionable HTTP errors", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Response.json({ message: "请等待60秒后再发送验证码" }, { status: 429 })));
    await expect(jsonRequest("/")).rejects.toMatchObject({ kind: "http", status: 429, message: "请等待60秒后再发送验证码" });
  });
});
describe("persistent player and queue", () => {
  it("deduplicates queue entries and preserves the same Audio across store consumers", async () => {
    const p = usePlayerStore();
    const audio = p.engine.audio;
    p.setResolver(async () => ({ url: "/fixture.wav" }));
    p.enqueue(song("1"));
    p.enqueue(song("1"));
    p.enqueue(song("2"));
    expect(p.queue).toHaveLength(2);
    await p.select(0);
    expect(usePlayerStore().engine.audio).toBe(audio);
    expect(p.playing).toBe(true);
    await p.toggle();
    expect(p.playing).toBe(false);
    await p.toggle();
    expect(p.playing).toBe(true);
  });
  it("ignores a stale media response when a later selection finishes first", async () => {
    const p = usePlayerStore();
    let resolveFirst!: (value: { url: string }) => void;
    p.setResolver(async (item) =>
      item.songId === "1"
        ? new Promise((resolve) => {
            resolveFirst = resolve;
          })
        : { url: "/second.wav" },
    );
    p.enqueue(song("1"));
    p.enqueue(song("2"));
    const first = p.select(0);
    await p.select(1);
    resolveFirst({ url: "/stale.wav" });
    await first;
    expect(p.engine.audio.src).toBe("/second.wav");
    expect(p.current?.songId).toBe("2");
  });
  it("stops removed current tracks and advances after ended", async () => {
    const p = usePlayerStore();
    p.setResolver(async (item) => ({ url: `/${item.songId}.wav` }));
    p.enqueue(song("1"));
    p.enqueue(song("2"));
    await p.select(0);
    p.playMode = "sequence";
    p.engine.audio.dispatchEvent(new Event("ended"));
    await vi.waitFor(() => expect(p.current?.songId).toBe("2"));
    p.remove(0);
    expect(p.index).toBe(0);
    p.remove(0);
    expect(p.current).toBeUndefined();
    expect(p.playing).toBe(false);
  });
  it("clamps seek and volume and cancels preparation without late playback", async () => {
    const p = usePlayerStore();
    let resolve!: (value: { url: string }) => void;
    p.setResolver(
      async () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    p.enqueue(song("1"));
    const pending = p.select(0);
    p.stop();
    resolve({ url: "/cancelled.wav" });
    await pending;
    expect(p.engine.audio.src).toBe("");
    expect(p.busy).toBe(false);
    p.seek(100);
    expect(p.engine.audio.currentTime).toBe(60);
    p.setVolume(-5);
    expect(p.volume).toBe(0);
  });
  it("repeats a single track without reloading media and wraps sequential navigation", async () => {
    const p = usePlayerStore();
    const resolver = vi.fn(async (item: Song) => ({ url: `/${item.songId}.wav` }));
    p.setResolver(resolver);
    p.enqueue(song("1")); p.enqueue(song("2"));
    await p.select(1);
    p.engine.audio.currentTime = 60;
    p.engine.audio.dispatchEvent(new Event("ended"));
    await vi.waitFor(() => expect(p.engine.audio.currentTime).toBe(0));
    expect(p.current?.songId).toBe("2");
    expect(resolver).toHaveBeenCalledTimes(1);
    p.playMode = "sequence";
    p.engine.audio.dispatchEvent(new Event("ended"));
    await vi.waitFor(() => expect(p.current?.songId).toBe("1"));
    await p.previous(); expect(p.current?.songId).toBe("2");
    await p.next(); expect(p.current?.songId).toBe("1");
  });
  it("randomly picks a different track and handles one or no tracks", async () => {
    const p = usePlayerStore();
    p.setResolver(async () => ({ url: "/fixture.wav" }));
    p.playMode = "random";
    await p.next(); await p.previous(); expect(p.index).toBe(-1);
    p.enqueue(song("1")); await p.next(); await p.next(); expect(p.index).toBe(0);
    p.enqueue(song("2")); p.enqueue(song("3"));
    const random = vi.spyOn(Math, "random").mockReturnValue(0);
    try {
      await p.next(); expect(p.index).toBe(1);
      random.mockReturnValue(.999999);
      await p.next(); expect(p.index).toBe(0);
    } finally { random.mockRestore(); }
  });
  it("restores the selected volume after muting, including slider changes", () => {
    const p = usePlayerStore();
    p.setVolume(.42); p.toggleMute(); expect(p.engine.audio.volume).toBe(0);
    p.toggleMute(); expect(p.engine.audio.volume).toBe(.42);
    p.setVolume(.2); p.setVolume(0); p.toggleMute(); expect(p.volume).toBe(.2);
  });
  it("requires account login before making media requests", async () => {
    const fetcher = vi.fn(); vi.stubGlobal("fetch", fetcher);
    const p = usePlayerStore(); p.enqueue(song("1")); await p.select(0);
    expect(p.message).toContain("请先登录");
    expect(p.playing).toBe(false); expect(fetcher).not.toHaveBeenCalled();
  });
  it("uses the account access token for both media preparation and playback", async () => {
    vi.stubGlobal("window", { location: { origin: "https://app.example.test" } });
    const fetcher = vi.fn(async (url: string) => Response.json(url.includes("/session/login")
      ? { success: true, user: { id: "1", username: "测试" }, tokens: { accessToken: "account-access", refreshToken: "renewal" } }
      : url.includes("processMp3") ? { success: true } : { state: "READY", ready: true }));
    vi.stubGlobal("fetch", fetcher);
    await useSessionStore().login("test@example.test", "password");
    const p = usePlayerStore();
    const load = vi.spyOn(p.engine, "load").mockResolvedValue(true);
    p.enqueue(song("1")); await p.select(0);
    const requests = fetcher.mock.calls as unknown as [string, RequestInit][];
    expect(requests.slice(1)).toHaveLength(2);
    for (const [, init] of requests.slice(1)) expect(init.headers).toEqual({ Authorization: "Bearer account-access" });
    expect(load).toHaveBeenCalledWith(expect.objectContaining({ token: "account-access", hls: true }));
    expect(p.playing).toBe(true);
  });
});
