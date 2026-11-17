<script setup lang="ts">
function scrollToSongs() { document.getElementById("search")?.scrollIntoView({ behavior: "smooth" }); }
// The original homepage used these editorial examples, independently of search data.
const playlists = [
  ['治愈系纯音乐 · 放松心情', '24首 · 轻音乐馆'],
  ['华语经典 · 那些年的回忆', '45首 · 经典老歌'],
  ['欧美流行 · 动感节奏', '38首 · 潮流前线'],
  ['国风新势力 · 诗意旋律', '29首 · 国风音乐馆'],
  ['深夜电台 · 治愈人声', '22首 · 情感音乐'],
];
const albums = [['时光里的我们', '林晓峰'], ['星辰大海', '张碧晨'], ['城市微光', '周深'], ['荒野之梦', '许巍'], ['昨日青空', '尤长靖'], ['心之焰', 'GAI周延']];
</script>
<template>
  <section class="discovery-section" aria-labelledby="recommend-heading">
    <div class="section-heading"><h2 id="recommend-heading">为你推荐</h2><a href="#" @click.prevent="scrollToSongs">查看更多</a></div>
    <div class="playlist-strip">
      <article v-for="(item, i) in playlists" :key="item[0]" class="discovery-card playlist-card">
        <img :src="`https://picsum.photos/300/300?random=${10 + i}`" :alt="item[0]" loading="lazy" />
        <div><h3>{{ item[0] }}</h3><p>{{ item[1] }}</p></div>
      </article>
    </div>
  </section>
  <section class="discovery-section" aria-labelledby="albums-heading">
    <div class="section-heading"><h2 id="albums-heading">热门专辑</h2><RouterLink :to="{ path: '/', query: { q: '', category: 'albums' } }">更多专辑</RouterLink></div>
    <div class="album-grid">
      <article v-for="(item, i) in albums" :key="item[0]" class="discovery-card album-card">
        <div class="album-image"><img :src="`https://picsum.photos/300/300?random=${20 + i}`" :alt="item[0]" loading="lazy" /><div class="album-overlay"><RouterLink :to="{ path: '/', query: { q: item[0] } }" :aria-label="`搜索专辑 ${item[0]}`">搜索歌曲</RouterLink></div></div>
        <h3>{{ item[0] }}</h3><p>歌手：{{ item[1] }}</p>
      </article>
    </div>
  </section>
</template>
