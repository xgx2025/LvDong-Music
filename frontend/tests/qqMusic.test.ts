import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useSessionStore } from "../src/stores/session";
import { qqMusic, cancelQqTask, loadAllQqSongs } from "../src/api/qqMusic";
import { usePlayerStore } from "../src/stores/player";
import type { Song } from "../src/types";

beforeEach(() => {
  setActivePinia(createPinia());
  vi.stubGlobal("window", { location: { origin: "https://site.test" } });
  vi.stubGlobal("sessionStorage", { getItem: () => null, removeItem: () => {} });
  const session = useSessionStore();
  session.user = { id: "1", username: "tester", email: "test@example.test" };
  session.accessToken = "website-token";
});
afterEach(() => vi.unstubAllGlobals());

it("uses website authorization only and refuses unauthenticated connections", async () => {
  const fetcher = vi.fn(async () => Response.json({ code: 0, data: { configured: true, connected: false } }));
  vi.stubGlobal("fetch", fetcher);
  await qqMusic.status();
  expect(fetcher.mock.calls[0]).toEqual(expect.arrayContaining(["https://site.test/api/v1/music-providers/qq/connection"]));
  expect((fetcher.mock.calls as unknown as [string, RequestInit][])[0]![1].headers).toEqual({ Authorization: "Bearer website-token" });
  useSessionStore().clear();
  await expect(qqMusic.start("qqmusic")).rejects.toThrow("请先登录");
  expect(fetcher).toHaveBeenCalledTimes(1);
});

it("cancels a late QR creation with its original credential after an account switch", async () => {
  let release!: (value: Response) => void;
  const fetcher = vi.fn((url: string, init: RequestInit) => init.method === "POST"
    ? new Promise<Response>(resolve => { release = resolve; })
    : Promise.resolve(Response.json({ code: 0, data: null })));
  vi.stubGlobal("fetch", fetcher);
  const pending = expect(qqMusic.start("qqmusic")).rejects.toThrow("登录状态已改变");
  const session = useSessionStore(); session.clear();
  session.user = { id: "2", username: "second", email: "second@example.test" }; session.accessToken = "other-token";
  release(Response.json({ code: 0, data: { id: "old-task" } }));
  await pending;
  expect(fetcher).toHaveBeenCalledWith("https://site.test/api/v1/music-providers/qq/login/old-task", expect.objectContaining({ method: "DELETE", headers: { Authorization: "Bearer website-token" } }));
});

it("surfaces connection failures and never treats business rejection as success", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => Response.json({ code: 503, message: "QQ 服务暂不可用" })));
  await expect(qqMusic.capabilities()).rejects.toThrow("QQ 服务暂不可用");
  vi.stubGlobal("fetch", vi.fn(async () => Response.json({ message: "请稍后刷新二维码" }, { status: 429 })));
  await expect(qqMusic.start("qqmusic")).rejects.toThrow("请稍后刷新二维码");
});

it("encodes the task path and sends no token in the URL", async () => {
  const fetcher = vi.fn(async () => Response.json({ code: 0, data: null }));
  vi.stubGlobal("fetch", fetcher);
  await cancelQqTask("task/one", "original-token");
  expect(fetcher).toHaveBeenCalledWith("https://site.test/api/v1/music-providers/qq/login/task%2Fone", expect.objectContaining({ method: "DELETE" }));
});

const song = (id: string, provider: "local" | "qq" = "qq") => ({ songId: id, provider, providerSongId: id, mediaMid: "media0", songName: id, singers: [] }) as unknown as Song;

it("loads all playlist pages before exposing queue content and deduplicates tracks", async () => {
  const fetcher = vi.fn(async (url: string) => {
    const offset = new URL(url).searchParams.get("offset");
    return Response.json({ code: 0, data: offset === "0"
      ? { items: [song("mid1"), song("mid2")], total: 3, nextOffset: 2, more: true }
      : { items: [song("mid2"), song("mid3")], total: 3, nextOffset: 4, more: false } });
  });
  vi.stubGlobal("fetch", fetcher);
  const progress = vi.fn();
  expect((await loadAllQqSongs("imported", "snapshot", new AbortController().signal, progress)).map(item => item.providerSongId)).toEqual(["mid1", "mid2", "mid3"]);
  expect(progress).toHaveBeenLastCalledWith(3);
  expect(fetcher).toHaveBeenCalledTimes(2);
});

it("rejects repeated and empty continuation pages instead of looping or accepting truncation", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => Response.json({ code: 0, data: {
    items: [song("same")], total: 3, nextOffset: Number(new URL(url).searchParams.get("offset")) + 1, more: true,
  } })));
  await expect(loadAllQqSongs("remote", "123", new AbortController().signal, () => {})).rejects.toThrow("未完整加载");
  vi.stubGlobal("fetch", vi.fn(async () => Response.json({ code: 0, data: { items: [], total: 3, nextOffset: 0, more: true } })));
  await expect(loadAllQqSongs("remote", "123", new AbortController().signal, () => {})).rejects.toThrow("未完整加载");
});

it("aborts playlist loading without requesting more pages", async () => {
  const controller = new AbortController(); controller.abort();
  const fetcher = vi.fn(); vi.stubGlobal("fetch", fetcher);
  await expect(loadAllQqSongs("remote", "123", controller.signal, () => {})).rejects.toMatchObject({ name: "AbortError" });
  expect(fetcher).not.toHaveBeenCalled();
});

class FakeAudio extends EventTarget {
  preload = ""; volume = 1; src = ""; currentTime = 0; duration = 60;
  async play() { this.dispatchEvent(new Event("playing")); }
  pause() { this.dispatchEvent(new Event("pause")); }
  load() { }
  removeAttribute() { this.src = ""; }
}
it("keeps QQ and local IDs distinct when loading batches into the queue", () => {
  vi.stubGlobal("Audio", FakeAudio);
  const player = usePlayerStore();
  expect(player.enqueueMany([song("same", "local"), song("same"), song("same")])).toBe(2);
  expect(player.queue).toHaveLength(2);
  expect(player.enqueue(song("same"))).toBe(1);
});

it("resolves QQ playback through the website API and retains an explicit preview notice", async () => {
  vi.stubGlobal("Audio", FakeAudio);
  const fetcher = vi.fn(async () => Response.json({ code: 0, data: { url: "https://isure.stream.qqmusic.qq.com/song.mp3", preview: true, quality: "standard" } }));
  vi.stubGlobal("fetch", fetcher);
  const player = usePlayerStore();
  await player.select(player.enqueue(song("mid0")));
  expect(player.engine.audio.src).toBe("https://isure.stream.qqmusic.qq.com/song.mp3");
  expect(player.preview).toBe(true);
  expect(player.message).toContain("试听片段");
  expect(fetcher).toHaveBeenCalledWith("https://site.test/api/v1/music-providers/qq/playback", expect.objectContaining({ method: "POST", body: JSON.stringify({ songMid: "mid0", mediaMid: "media0" }) }));
  expect(fetcher).toHaveBeenCalledTimes(1);
});
