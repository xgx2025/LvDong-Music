<script setup lang="ts">
import { usePlayerStore } from "../stores/player";
import CoverArt from "./CoverArt.vue";
import LyricsPanel from "./LyricsPanel.vue";
const p = usePlayerStore();
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
            :disabled="p.index <= 0"
            @click="p.select(p.index - 1)"
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
            :disabled="p.index + 1 >= p.queue.length || !p.queue.length"
            @click="p.select(p.index + 1)"
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
      {{ p.message }} <RouterLink to="/settings">连接设置</RouterLink>
    </p>
  </section>
</template>
