<script setup lang="ts">
import AppIcon from "./AppIcon.vue";
</script>
<template>
  <nav class="music-library-nav" aria-label="我的音乐">
    <RouterLink to="/favorites"><AppIcon name="heart" />本站收藏</RouterLink>
    <RouterLink to="/qq-playlists"><AppIcon name="music" />QQ 音乐歌单</RouterLink>
    <RouterLink to="/queue"><AppIcon name="list" />播放队列</RouterLink>
  </nav>
</template>
<style scoped>
.music-library-nav { display: flex; gap: 8px; flex-wrap: wrap; margin: 0 0 28px; padding: 5px; width: fit-content; max-width: 100%; background: #ffffff70; border: 1px solid #ffffffb0; border-radius: 12px; }
.music-library-nav a { display: inline-flex; align-items: center; gap: 8px; padding: 11px 16px; border: 0; border-radius: 8px; color: #526077; font-size: 13px; }
.music-library-nav a.router-link-exact-active { color: var(--primary); background: #fff; box-shadow: 0 2px 8px #213c7010; }
@media(max-width: 480px) { .music-library-nav { width: 100%; gap: 2px; } .music-library-nav a { flex: 1; justify-content: center; padding: 10px 6px; font-size: 12px; } }
</style>
