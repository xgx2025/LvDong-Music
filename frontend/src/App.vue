<script setup lang="ts">
import PlayerBar from "./components/PlayerBar.vue";
import { usePlayerStore } from "./stores/player";
import { useSessionStore } from "./stores/session";
import { watch } from "vue";
import { useRoute, useRouter } from "vue-router";
const player = usePlayerStore();
const session = useSessionStore(), route = useRoute(), router = useRouter();
watch(() => session.revision, () => player.stop(), { flush: "sync" });
watch(() => session.authenticated, (loggedIn) => {
  if (!loggedIn && route.meta.requiresAuth) void router.replace({ path: "/login", query: { redirect: route.fullPath } });
});
</script>
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <div class="app-shell" :class="{ 'dark-theme': route.meta.dark, 'auth-layout': route.meta.authLayout }">
    <header v-show="!route.meta.authLayout" class="topbar">
      <div class="header-content">
        <RouterLink class="brand" to="/" aria-label="律动音乐首页"
        ><span class="brand-mark" aria-hidden="true">♪</span
        ><span>律动</span></RouterLink
      >
        <div id="header-search"></div>
        <nav aria-label="主导航">
          <RouterLink to="/">首页</RouterLink>
          <RouterLink to="/recognition">哼唱识曲</RouterLink>
          <RouterLink to="/upload">上传</RouterLink>
          <RouterLink to="/assistant">AI助手</RouterLink>
          <RouterLink to="/feedback">反馈</RouterLink>
          <RouterLink :to="session.authenticated ? '/profile' : '/login'">{{ session.authenticated ? '我的' : '登录' }}</RouterLink>
          <RouterLink to="/queue">
            播放队列<span>{{ player.queue.length.toString().padStart(2, "0") }}</span>
          </RouterLink>
          <RouterLink to="/game">小游戏</RouterLink>
        </nav>
      </div>
    </header>
    <div class="main-column">
      <main id="main" tabindex="-1"><RouterView /></main>
    </div>
  </div>
  <PlayerBar v-show="!route.meta.dark || player.queue.length > 0" :class="{ 'dark-player': route.meta.dark }" />
</template>
