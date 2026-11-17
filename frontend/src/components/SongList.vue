<script setup lang="ts">
import type { Song } from "../types";
import { usePlayerStore } from "../stores/player";
import CoverArt from "./CoverArt.vue";
import FavoriteButton from "./FavoriteButton.vue";
defineProps<{ songs: Song[] }>();
const player = usePlayerStore();
</script>
<template><div class="song-list"><article v-for="(song, i) in songs" :key="song.songId" class="song-row"><span class="row-number">{{ i + 1 }}</span><CoverArt :path="song.coverUrl" :name="song.songName" /><div class="song-title"><RouterLink :to="`/song/${song.songId}`"><strong>{{ song.songName }}</strong></RouterLink><small>{{ song.singers?.map(s => s.name).join(' / ') || '未知歌手' }}</small></div><span class="song-album">{{ song.albumName || "单曲" }}</span><span class="song-duration">{{ song.duration }}</span><div class="song-actions"><button :aria-label="`播放 ${song.songName}`" @click="player.select(player.enqueue(song))">播放</button><FavoriteButton :song-id="song.songId" /></div></article></div></template>
