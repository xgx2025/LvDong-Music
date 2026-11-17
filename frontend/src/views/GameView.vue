<script setup lang="ts">
const gameUrl = `${import.meta.env.BASE_URL}game.html`;
</script>
<template>
  <section class="content-page game-page">
    <div class="section-heading"><h1>坦克大战</h1><RouterLink to="/">返回首页</RouterLink></div>
    <iframe :src="gameUrl" title="坦克大战小游戏" class="game-frame"></iframe>
  </section>
</template>
