<script setup lang="ts">
import { usePlayerStore } from "../stores/player";
import MusicLibraryNav from "../components/MusicLibraryNav.vue";
import CoverArt from "../components/CoverArt.vue";
import { songKey } from "../types";
const player = usePlayerStore();
</script>
<template>
  <section class="inner-page">
    <MusicLibraryNav />
    <div class="section-heading">
      <h2>
        播放队列 <span class="count">{{ player.queue.length }}</span>
      </h2>
      <button :disabled="!player.queue.length" @click="player.clear">
        清空队列
      </button>
    </div>
    <p class="subtle">队列保留在当前页面会话中，切换页面不会打断播放。</p>
    <div v-if="!player.queue.length" class="empty-state">
      <h3>给下一首留个位置</h3>
      <p>在发现页搜索音乐，加入你的播放队列。</p>
      <RouterLink class="primary" to="/">发现音乐</RouterLink><RouterLink to="/qq-playlists">从 QQ 歌单添加</RouterLink>
    </div>
    <ol v-else class="queue-list">
      <li
        v-for="(song, i) in player.queue"
        :key="songKey(song)"
        :class="{ active: player.index === i }"
      >
        <span class="row-number">{{ i + 1 }}</span
        ><CoverArt :path="song.coverUrl" :name="song.songName" />
        <div class="song-title">
          <strong>{{ song.songName }}</strong
          ><small>{{ song.singers.map((s) => s.name).join(" / ") }}</small>
        </div>
        <span v-if="player.index === i" class="queue-state">{{
          player.busy ? "准备中" : player.playing ? "正在播放" : "已选中"
        }}</span
        ><button
          :aria-label="`播放 ${song.songName}`"
          @click="player.select(i)"
        >
          播放</button
        ><button
          :aria-label="`移除 ${song.songName}`"
          @click="player.remove(i)"
        >
          移除
        </button>
      </li>
    </ol>
  </section>
</template>
