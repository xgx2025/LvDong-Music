import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { Song } from "../types";
import { AudioEngine } from "../player/engine";
import { prepareLegacy, type MediaSource } from "../api/legacy";
import { useSessionStore } from "./session";
import { RequestError } from "../api/http";

export const usePlayerStore = defineStore("player", () => {
  const engine = new AudioEngine();
  const queue = ref<Song[]>([]),
    index = ref(-1),
    playing = ref(false),
    busy = ref(false);
  const position = ref(0),
    duration = ref(0),
    volume = ref(0.75),
    message = ref(""),
    expanded = ref(false);
  const playMode = ref<"sequence" | "single" | "random">("single");
  const modeLabel = computed(() => ({ sequence: "顺序播放", single: "单曲循环", random: "随机播放" })[playMode.value]);
  let previousVolume = volume.value;
  const current = computed(() => queue.value[index.value]);
  let controller: AbortController | undefined;
  let loaded = false;
  // Isolated tests inject a resolver; the shipped UI resolves authenticated Boot media.
  let resolver = async (song: Song, signal: AbortSignal): Promise<MediaSource> => {
    const session = useSessionStore(), version = session.revision;
    if (!session.authenticated) throw new RequestError("http", "请先登录后播放音乐", 401);
    const prepare = () => prepareLegacy(song.songId, session.accessToken, signal, text => { message.value = text; });
    try { return await prepare(); }
    catch (error) {
      if (!(error instanceof RequestError) || error.status !== 401 || !session.authenticated || signal.aborted) throw error;
      await session.refresh();
      if (version !== session.revision || signal.aborted) throw new Error("登录状态已改变");
      return prepare();
    }
  };
  engine.audio.volume = volume.value;
  engine.audio.addEventListener("timeupdate", () => {
    position.value = engine.audio.currentTime;
  });
  engine.audio.addEventListener("durationchange", () => {
    duration.value = Number.isFinite(engine.audio.duration)
      ? engine.audio.duration
      : 0;
  });
  engine.audio.addEventListener("playing", () => {
    playing.value = true;
    message.value = "";
  });
  engine.audio.addEventListener("pause", () => {
    playing.value = false;
  });
  engine.audio.addEventListener("error", () => {
    playing.value = false;
    busy.value = false;
    loaded = false;
    message.value = "音频加载失败，请检查媒体服务、凭证或网络后重试";
  });
  engine.audio.addEventListener("ended", () => {
    if (index.value < 0 || !current.value || !loaded) return;
    if (playMode.value === "single") {
      engine.seek(0);
      void engine.play().catch(() => { playing.value = false; message.value = "浏览器未开始播放，请再次点击播放"; });
    } else void next();
  });
  function cycleMode() {
    playMode.value = playMode.value === "sequence" ? "single" : playMode.value === "single" ? "random" : "sequence";
  }
  function next() {
    const count = queue.value.length;
    if (!count) return;
    if (index.value < 0) return select(0);
    // Select among the other tracks without retry loops, including a one-track queue.
    const target = playMode.value === "random" && count > 1
      ? (index.value + 1 + Math.floor(Math.random() * (count - 1))) % count
      : playMode.value === "single" ? index.value : (index.value + 1) % count;
    return select(target);
  }
  function previous() {
    if (queue.value.length) return select(index.value < 0 ? 0 : (index.value - 1 + queue.value.length) % queue.value.length);
  }
  function enqueue(song: Song) {
    const found = queue.value.findIndex((item) => item.songId === song.songId);
    if (found >= 0) return found;
    queue.value.push(song);
    return queue.value.length - 1;
  }
  async function select(target: number) {
    if (!queue.value[target]) return;
    controller?.abort();
    controller = new AbortController();
    const request = controller;
    engine.clear();
    loaded = false;
    playing.value = false;
    position.value = 0;
    duration.value = 0;
    index.value = target;
    busy.value = true;
    message.value = "正在连接媒体服务…";
    try {
      const source = await resolver(queue.value[target]!, request.signal);
      if (request.signal.aborted) return;
      if (!(await engine.load(source)) || request.signal.aborted) return;
      loaded = true;
      await engine.play();
    } catch (error) {
      if (!request.signal.aborted)
        message.value =
          error instanceof Error ? error.message : "播放未开始，请重试";
    } finally {
      if (controller === request) busy.value = false;
    }
  }
  async function toggle() {
    if (busy.value) {
      stop();
      return;
    }
    if (playing.value) engine.pause();
    else if (loaded) {
      try {
        await engine.play();
      } catch {
        message.value = "浏览器未开始播放，请再次点击播放";
      }
    } else if (current.value) await select(index.value);
    else if (queue.value.length) await select(0);
  }
  function stop() {
    controller?.abort();
    engine.clear();
    loaded = false;
    busy.value = false;
    playing.value = false;
    position.value = 0;
    duration.value = 0;
    message.value = "";
  }
  function remove(target: number) {
    if (target === index.value) {
      stop();
      index.value = -1;
    } else if (target < index.value) index.value--;
    queue.value.splice(target, 1);
  }
  function setVolume(value: number) {
    if (!Number.isFinite(value)) return;
    volume.value = Math.min(1, Math.max(0, value));
    engine.audio.volume = volume.value;
    if (volume.value > 0) previousVolume = volume.value;
  }
  function toggleMute() {
    setVolume(volume.value > 0 ? 0 : previousVolume);
  }
  function clear() {
    stop();
    queue.value = [];
    index.value = -1;
  }
  return {
    queue,
    index,
    current,
    playing,
    busy,
    position,
    duration,
    volume,
    message,
    expanded,
    playMode,
    modeLabel,
    cycleMode,
    next,
    previous,
    enqueue,
    select,
    toggle,
    stop,
    remove,
    setVolume,
    toggleMute,
    clear,
    seek: (value: number) => engine.seek(value),
    setResolver: (next: typeof resolver) => {
      resolver = next;
    },
    engine,
  };
});
