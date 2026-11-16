<script setup lang="ts">
import PlayerBar from "./components/PlayerBar.vue";
import { usePlayerStore } from "./stores/player";
const player = usePlayerStore();
</script>
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/" aria-label="律动音乐首页"
        ><span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i></span
        ><span>律动<span class="brand-en">LVDONG MUSIC</span></span></RouterLink
      >
      <p class="nav-label">你的音乐空间</p>
      <nav aria-label="主导航">
        <RouterLink to="/">发现音乐<span>01</span></RouterLink
        ><RouterLink to="/queue"
          >播放队列<span>{{
            player.queue.length.toString().padStart(2, "0")
          }}</span></RouterLink
        ><RouterLink to="/settings">连接设置<span>03</span></RouterLink>
      </nav>
      <div class="side-note">
        <span class="tiny-disc" aria-hidden="true"></span>
        <p>让音乐<br />留在此刻。</p>
        <small>一首歌，一段自己的时间。</small>
      </div>
      <p class="version">PUBLIC PREVIEW · 0.1</p>
    </aside>
    <div class="main-column">
      <header class="topbar">
        <span>听见生活的另一面</span
        ><RouterLink to="/settings" class="connection"
          ><i aria-hidden="true"></i>匿名浏览</RouterLink
        >
      </header>
      <main id="main" tabindex="-1"><RouterView /></main>
      <footer class="page-footer">
        <span>律动音乐</span><span>慢一点，听完整首歌。</span>
      </footer>
    </div>
  </div>
  <PlayerBar />
</template>
