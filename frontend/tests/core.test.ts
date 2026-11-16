import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { apiRequest, jsonRequest, RequestError } from "../src/api/http";
import { usePlayerStore } from "../src/stores/player";
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
});
