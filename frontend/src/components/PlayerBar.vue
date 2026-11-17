<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, watch } from "vue";
import { useRoute } from "vue-router";
import { usePlayerStore } from "../stores/player";
import { useSessionStore } from "../stores/session";
import { useCoverTheme } from "../player/coverTheme";
import { coverUrl } from "../api/legacy";
import AppIcon from "./AppIcon.vue";
import CoverArt from "./CoverArt.vue";
import FavoriteButton from "./FavoriteButton.vue";
import LyricsPanel from "./LyricsPanel.vue";
const p = usePlayerStore();
const session = useSessionStore(), route = useRoute();
const background = computed(() => coverUrl(p.current?.coverUrl || null));
const coverColor = useCoverTheme(background);
let suppressClickUntil = 0;
function toggleExpanded() { if (Date.now() >= suppressClickUntil) p.expanded = !p.expanded; }
let gesture: { x: number; y: number; expanded: boolean } | undefined;
function gestureStart(event: TouchEvent) {
  gesture = undefined;
  if (event.touches.length !== 1 || (p.expanded && (event.target as HTMLElement).closest("button, a"))) return;
  const touch = event.touches[0]!;
  gesture = { x: touch.clientX, y: touch.clientY, expanded: p.expanded };
}
function gestureEnd(event: TouchEvent) {
  const start = gesture; gesture = undefined;
  if (!start || event.touches.length || event.changedTouches.length !== 1 || p.expanded !== start.expanded) return;
  const touch = event.changedTouches[0]!, dy = touch.clientY - start.y;
  if (Math.abs(dy) < 64 || Math.abs(touch.clientX - start.x) > Math.abs(dy) * .6) return;
  if ((!start.expanded && dy < 0) || (start.expanded && dy > 0)) { p.expanded = !start.expanded; suppressClickUntil = Date.now() + 400; }
}
const loginLink = computed(() => ({ path: "/login", query: { redirect: route.fullPath } }));
watch(() => route.fullPath, () => { p.expanded = false; });
function keyboard(event: KeyboardEvent) {
  if (event.key === 'Escape' && p.expanded && !document.querySelector('dialog[open]')) { p.expanded = false; event.preventDefault(); return; }
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
    :aria-label="p.expanded ? '正在播放' : '常驻播放器'"
    class="player"
    :class="{ expanded: p.expanded }"
    :style="{ '--player-cover-color': coverColor }"
  >
    <img v-if="p.expanded && background" :src="background" alt="" aria-hidden="true" class="player-cover-background" referrerpolicy="no-referrer" />
    <div v-if="p.expanded" class="now-playing">
      <div class="expanded-player-heading" @touchstart.passive="gestureStart" @touchend.passive="gestureEnd" @touchcancel="gesture = undefined"><span class="player-gesture-handle" aria-hidden="true"></span><button aria-label="收起播放器" @click="p.expanded = false"><AppIcon name="chevron-down" />收起</button><span>律动</span><FavoriteButton v-if="p.current" :song-id="p.current.songId" /></div>
      <button class="player-artwork" :class="{ playing: p.playing }" :aria-label="p.playing ? '暂停当前歌曲' : '播放当前歌曲'" :disabled="!p.current" @click="p.toggle()"><CoverArt :path="p.current?.coverUrl" :name="p.current?.songName" /><span class="artwork-play" aria-hidden="true"><AppIcon :name="p.playing ? 'pause' : 'play'" /></span></button>
      <h2>{{ p.current?.songName || "下一首，留给你选择" }}</h2>
      <p>
        {{
          p.current?.singers.map((s) => s.name).join(" / ") ||
          "从搜索结果加入一首喜欢的歌"
        }}
      </p>
      <p v-if="p.current?.albumName" class="playing-album">{{ p.current.albumName }}</p>
      <LyricsPanel :path="p.current?.lyricUrl" />
      <RouterLink v-if="p.current && p.current.provider !== 'qq'" :to="`/song/${p.current.songId}`" @click="p.expanded = false"><AppIcon name="chat" />查看歌曲与评论</RouterLink>
      <p v-if="p.current?.provider === 'qq'">QQ 音乐{{ p.preview ? ' · 试听片段' : '' }}</p>
    </div>
    <div class="player-row">
      <button
        class="track-info"
        @touchstart.passive="gestureStart" @touchend.passive="gestureEnd" @touchcancel="gesture = undefined"
        :aria-expanded="p.expanded"
        aria-label="展开或收起正在播放"
        @click="toggleExpanded"
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
            <AppIcon name="previous" /></button
          ><button
            class="play-button"
            :aria-label="p.busy ? '取消准备' : p.playing ? '暂停' : '播放'"
            :disabled="!p.queue.length"
            @click="p.toggle()"
          >
            <AppIcon :name="p.busy ? 'stop' : p.playing ? 'pause' : 'play'" /></button
          ><button
            aria-label="下一首"
            :disabled="!p.queue.length"
            @click="p.next()"
          >
            <AppIcon name="next" />
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
        <button class="play-mode" :aria-label="`播放模式：${p.modeLabel}`" :title="`播放模式：${p.modeLabel}，点击切换`" @click="p.cycleMode()"><AppIcon :name="p.playMode === 'single' ? 'repeat-one' : p.playMode === 'random' ? 'shuffle' : 'repeat'" /><span class="sr-only">{{ p.modeLabel }}</span></button>
        <button :aria-label="p.volume === 0 ? '恢复音量' : '静音'" :title="p.volume === 0 ? '恢复音量' : '静音'" :aria-pressed="p.volume === 0" @click="p.toggleMute()"><AppIcon :name="p.volume === 0 ? 'volume-off' : p.volume < .5 ? 'volume-low' : 'volume'" /></button>
        <label
          ><span class="sr-only">音量</span><input
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
          ><AppIcon name="list" /><span class="sr-only">队列</span> {{ p.queue.length }}</RouterLink
        ><button :aria-expanded="p.expanded" :aria-label="p.expanded ? '收起' : '展开'" @click="p.expanded = !p.expanded">
          <AppIcon :name="p.expanded ? 'chevron-down' : 'chevron-up'" />
        </button>
      </div>
    </div>
    <p v-if="p.message" class="player-message" role="status">
      {{ p.message }} <RouterLink v-if="!session.authenticated" :to="loginLink">前往登录</RouterLink>
    </p>
  </section>
</template>
