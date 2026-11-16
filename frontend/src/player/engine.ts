import type { MediaSource } from "../api/legacy";

// Created once by the application store, never by a route component.
export class AudioEngine {
  readonly audio: HTMLAudioElement;
  private hls?: import("hls.js").default;
  private generation = 0;
  constructor(audio = new Audio()) {
    this.audio = audio;
    audio.preload = "metadata";
  }
  clear() {
    this.generation++;
    this.hls?.destroy();
    this.hls = undefined;
    this.audio.pause();
    this.audio.removeAttribute("src");
    this.audio.load();
  }
  async load(source: MediaSource) {
    this.clear();
    const generation = this.generation;
    if (source.hls) {
      const { default: Hls } = await import("hls.js");
      if (generation !== this.generation) return false;
      if (Hls.isSupported()) {
        this.hls = new Hls({
          xhrSetup: (xhr, url) => {
            if (
              new URL(url, location.href).origin !== new URL(source.url).origin
            )
              throw new Error("媒体请求来源不匹配");
            if (source.token)
              xhr.setRequestHeader("Authorization", `Bearer ${source.token}`);
          },
        });
        this.hls.on(Hls.Events.ERROR, (_, data) => {
          if (data.fatal) this.audio.dispatchEvent(new Event("error"));
        });
        this.hls.loadSource(source.url);
        this.hls.attachMedia(this.audio);
      } else if (this.audio.canPlayType("application/vnd.apple.mpegurl")) {
        const url = new URL(source.url);
        if (source.token) url.searchParams.set("token", source.token);
        this.audio.src = url.href;
      } else throw new Error("当前浏览器不支持 HLS 播放");
    } else this.audio.src = source.url;
    return true;
  }
  play() {
    return this.audio.play();
  }
  pause() {
    this.audio.pause();
  }
  seek(value: number) {
    if (Number.isFinite(this.audio.duration))
      this.audio.currentTime = Math.max(
        0,
        Math.min(value, this.audio.duration),
      );
  }
}
