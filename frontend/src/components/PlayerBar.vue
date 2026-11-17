<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount } from "vue";
import { useRoute } from "vue-router";
import { usePlayerStore } from "../stores/player";
import { useSessionStore } from "../stores/session";
import { coverUrl } from "../api/legacy";
import CoverArt from "./CoverArt.vue";
import LyricsPanel from "./LyricsPanel.vue";
const p = usePlayerStore();
const session = useSessionStore(), route = useRoute();
const background = computed(() => coverUrl(p.current?.coverUrl || null));
const loginLink = computed(() => ({ path: "/login", query: { redirect: route.fullPath } }));
function keyboard(event: KeyboardEvent) {
  if (event.code !== "Space" || event.repeat || event.isComposing || event.defaultPrevented
    || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey || route.meta.authLayout || !p.queue.length) return;
  if (event.target instanceof HTMLElement && event.target.closest('input, textarea, select, button, a, [contenteditable], [role="textbox"], [role="slider"]')) return;
  event.preventDefault();
  void p.toggle();
}
onMounted(() => document.addEventListener("keydown", keyboard));
onBeforeUnmount(() => document.removeEventListener("keydown", keyboard));
const time = (value: number) =>
  `${Math.floor(value / 60)}:${Math.floor(value % 60)
    .toString()
    .padStart(2, "0")}`;
</script>
<template>
  <section
    class="player"
    aria-label="常驻播放器"
    :class="{ expanded: p.expanded }"
  >
    <img v-if="p.expanded && background" :src="background" alt="" aria-hidden="true" class="player-cover-background" referrerpolicy="no-referrer" />
    <div v-if="p.expanded" class="now-playing">
      <p class="eyebrow">NOW PLAYING</p>
      <CoverArt :path="p.current?.coverUrl" :name="p.current?.songName" />
      <h2>{{ p.current?.songName || "下一首，留给你选择" }}</h2>
      <p>
        {{
          p.current?.singers.map((s) => s.name).join(" / ") ||
          "从搜索结果加入一首喜欢的歌"
        }}
      </p>
      <LyricsPanel :path="p.current?.lyricUrl" />
      <RouterLink v-if="p.current" :to="`/song/${p.current.songId}`">查看歌曲与评论</RouterLink>
    </div>
    <div class="player-row">
      <button
        class="track-info"
        :aria-expanded="p.expanded"
        aria-label="展开或收起正在播放"
        @click="p.expanded = !p.expanded"
      >
        <CoverArt
          :path="p.current?.coverUrl"
          :name="p.current?.songName"
        /><span
          ><strong>{{ p.current?.songName || "还没有正在播放的音乐" }}</strong
          ><small>{{
            p.current?.singers.map((s) => s.name).join(" / ") ||
            "从发现页开始你的队列"
          }}</small></span
        >
      </button>
      <div class="transport">
        <div class="transport-buttons">
          <button
            aria-label="上一首"
            :disabled="!p.queue.length"
            @click="p.previous()"
          >
            Ⅰ◀</button
          ><button
            class="play-button"
            :aria-label="p.busy ? '取消准备' : p.playing ? '暂停' : '播放'"
            :disabled="!p.queue.length"
            @click="p.toggle()"
          >
            {{ p.busy ? "■" : p.playing ? "Ⅱ" : "▶" }}</button
          ><button
            aria-label="下一首"
            :disabled="!p.queue.length"
            @click="p.next()"
          >
            ▶Ⅰ
          </button>
        </div>
        <label class="timeline"
          ><span>{{ time(p.position) }}</span
          ><input
            aria-label="播放进度"
            type="range"
            min="0"
            :max="p.duration || 1"
            step="0.1"
            :value="p.position"
            :disabled="!p.duration"
            @input="p.seek(Number(($event.target as HTMLInputElement).value))"
          /><span>{{ time(p.duration) }}</span></label
        >
      </div>
      <div class="player-tools">
        <button class="play-mode" :aria-label="`播放模式：${p.modeLabel}`" title="切换播放模式" @click="p.cycleMode()">{{ p.modeLabel }}</button>
        <button :aria-label="p.volume === 0 ? '恢复音量' : '静音'" :aria-pressed="p.volume === 0" @click="p.toggleMute()">{{ p.volume === 0 ? '恢复音量' : '静音' }}</button>
        <label
          >音量<input
            aria-label="音量"
            type="range"
            min="0"
            max="1"
            step="0.01"
            :value="p.volume"
            @input="
              p.setVolume(Number(($event.target as HTMLInputElement).value))
            " /></label
        ><RouterLink to="/queue" aria-label="查看播放队列"
          >队列 {{ p.queue.length }}</RouterLink
        ><button :aria-expanded="p.expanded" @click="p.expanded = !p.expanded">
          {{ p.expanded ? "收起" : "展开" }}
        </button>
      </div>
    </div>
    <p v-if="p.message" class="player-message" role="status">
      {{ p.message }} <RouterLink v-if="!session.authenticated" :to="loginLink">前往登录</RouterLink>
    </p>
  </section>
</template>
