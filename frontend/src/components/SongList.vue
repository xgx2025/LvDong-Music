<script setup lang="ts">
import AppIcon from "./AppIcon.vue";
import type { Song } from "../types";
import { songKey } from "../types";
import { usePlayerStore } from "../stores/player";
import CoverArt from "./CoverArt.vue";
import FavoriteButton from "./FavoriteButton.vue";
defineProps<{ songs: Song[] }>();
const player = usePlayerStore();
</script>
<template><div class="song-list"><article v-for="(song, i) in songs" :key="songKey(song)" class="song-row"><span class="row-number">{{ i + 1 }}</span><CoverArt :path="song.coverUrl" :name="song.songName" /><div class="song-title"><strong v-if="song.provider === 'qq'">{{ song.songName }}</strong><RouterLink v-else :to="`/song/${song.songId}`"><strong>{{ song.songName }}</strong></RouterLink><small>{{ song.singers?.map(s => s.name).join(' / ') || '未知歌手' }}<template v-if="song.provider === 'qq'"> · QQ 音乐</template></small></div><span class="song-album">{{ song.albumName || "单曲" }}</span><span class="song-duration">{{ song.duration }}</span><div class="song-actions"><button :aria-label="`播放 ${song.songName}`" @click="player.select(player.enqueue(song))"><AppIcon name="play" /><span class="sr-only">播放</span></button><button v-if="song.provider === 'qq'" :aria-label="`加入队列 ${song.songName}`" @click="player.enqueue(song)">加入队列</button><FavoriteButton v-else :song-id="song.songId" /></div></article></div></template>
