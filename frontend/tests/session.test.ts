import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useSessionStore } from "../src/stores/session";
import { parseLyrics, lyricIndex } from "../src/player/lyrics";
import { readFile } from "../src/api/account";

const actionOf = (url: string) => new URL(url).searchParams.get("action") || ({ refresh: "refreshToken" }[new URL(url).pathname.split("/").pop()!] ?? new URL(url).pathname.split("/").pop());
const user = { id: "9223372036854775807", username: "测试用户", email: "test@example.test" };
const result = { success: true, user, tokens: { accessToken: "old-access", refreshToken: "refresh" } };
let saved = new Map<string, string>();
beforeEach(() => {
  setActivePinia(createPinia()); saved = new Map();
  vi.stubGlobal("window", { location: { origin: "https://app.example.test" } });
  vi.stubGlobal("sessionStorage", { getItem: (key: string) => saved.get(key) || null, setItem: (key: string, value: string) => saved.set(key, value), removeItem: (key: string) => saved.delete(key) });
});
afterEach(() => vi.unstubAllGlobals());

it("coalesces concurrent 401s into one refresh and retries with the renewed token", async () => {
  let refreshes = 0;
  const fetcher = vi.fn(async (url: string, init: RequestInit) => {
    const action = actionOf(url);
    if (action === "login") return Response.json(result);
    if (action === "refreshToken") { refreshes++; await Promise.resolve(); return Response.json({ tokens: { accessToken: "new-access", refreshToken: "refresh" } }); }
    return (init.headers as Record<string, string>).Authorization === "Bearer old-access"
      ? new Response("", { status: 401 }) : Response.json({ success: true });
  });
  vi.stubGlobal("fetch", fetcher);
  const session = useSessionStore(); await session.login(user.email, "test-password", true);
  await Promise.all([session.request("userServlet", "getProfile"), session.request("favoriteServlet", "getFavorites"), session.request("singerServlet", "getStatus")]);
  expect(refreshes).toBe(1); expect(session.mediaToken).toBe("new-access");
  expect([...saved.values()][0]).not.toContain("test-password");
  expect(session.user?.id).toBe(user.id);
});

it("does not revive credentials when a refresh arrives after logout", async () => {
  let release!: (response: Response) => void;
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    const action = actionOf(url);
    if (action === "login") return Response.json(result);
    if (action === "refreshToken") return new Promise<Response>(resolve => { release = resolve; });
    return Response.json({ success: true });
  }));
  const session = useSessionStore(); await session.login(user.email, "password", true);
  const pending = expect(session.refresh()).rejects.toThrow("登录状态已改变");
  await Promise.resolve(); await session.logout();
  release(Response.json({ tokens: { accessToken: "late-access", refreshToken: "refresh" } }));
  await pending;
  expect(session.authenticated).toBe(false); expect(session.mediaToken).toBe(""); expect(saved.size).toBe(0);
});

it("clears all state after rejected renewal and retries a request at most once", async () => {
  let businessCalls = 0;
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    const action = actionOf(url);
    if (action === "login") return Response.json(result);
    if (action === "refreshToken") return Response.json({ success: false, message: "会话已撤销" });
    businessCalls++; return new Response("", { status: 401 });
  }));
  const session = useSessionStore(); await session.login(user.email, "password", true);
  await expect(session.request("userServlet", "getProfile")).rejects.toThrow("会话已撤销");
  expect(session.user).toBeUndefined(); expect(saved.size).toBe(0); expect(businessCalls).toBe(1);
});

it("rejects business errors instead of reporting a successful profile save", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => Response.json(actionOf(url) === "login" ? result : { success: false, message: "用户名已存在" })));
  const session = useSessionStore(); await session.login(user.email, "password");
  await expect(session.request("userServlet", "updateProfile", { username: "duplicate" })).rejects.toThrow("用户名已存在");
  expect(session.user?.username).toBe("测试用户"); expect(saved.size).toBe(0);
});

it("restores only after the server renews the tab session", async () => {
  saved.set("lvdong.vue.session", JSON.stringify({ ...result.user, user, ...result.tokens }));
  vi.stubGlobal("fetch", vi.fn(async () => Response.json({ tokens: { accessToken: "restored", refreshToken: "refresh" } })));
  const session = useSessionStore(); await session.restore();
  expect(session.authenticated).toBe(true); expect(session.mediaToken).toBe("restored");
});

it("parses multi-tag LRC, offset, fractions and finds the line after seeking", () => {
  const lines = parseLyrics("[offset:500]\n[00:02.50][00:04.005]两次出现\n[00:03]下一行\n[00:99]无效\n[ar:歌手]");
  expect(lines).toEqual([{ time: 2, text: "两次出现" }, { time: 2.5, text: "下一行" }, { time: 3.505, text: "两次出现" }]);
  expect(lyricIndex(lines, 1)).toBe(-1); expect(lyricIndex(lines, 3)).toBe(1); expect(lyricIndex(lines, 100)).toBe(2);
  expect(parseLyrics("纯文本歌词")).toEqual([]);
});

it("rejects unsafe or oversized uploads before reading them", async () => {
  await expect(readFile({ size: 4 * 1024 * 1024, type: "image/png" } as File, "image")).rejects.toThrow("大小限制");
  await expect(readFile({ size: 100, type: "text/html" } as File, "image")).rejects.toThrow("不支持");
});

it("routes migrated actions to Boot and leaves pending writes on the WAR", async () => {
  const fetcher = vi.fn(async (url: string) => Response.json(actionOf(url) === "login" ? result : { success: true }));
  vi.stubGlobal("fetch", fetcher);
  const session = useSessionStore(); await session.login(user.email, "password");
  await session.request("favoriteServlet", "addFavorite", { songId: "9007199254740993" });
  await session.request("commentServlet", "likeComment", { commentId: "7", like: true });
  expect(fetcher.mock.calls.map(([url]) => new URL(url).pathname)).toEqual([
    "/api/v1/session/login", "/api/v1/library/favorite", "/legacy/commentServlet",
  ]);
});
