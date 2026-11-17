<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { coverUrl } from "../api/legacy";
import { usePlayerStore } from "../stores/player";
import { parseLyrics, lyricIndex, type LyricLine } from "../player/lyrics";
import { qqMusic } from "../api/qqMusic";
import { useSessionStore } from "../stores/session";
const props = withDefaults(defineProps<{ path?: string | null; active?: boolean; qqSongId?: string | null }>(), { active: true });
const player = usePlayerStore();
const session = useSessionStore();
const failed = ref(false), retry = ref(0);
const lines = ref<LyricLine[]>([]), plain = ref(""), status = ref(""), container = ref<HTMLElement>();
const current = computed(() => props.active === false ? -1 : lyricIndex(lines.value, player.position));
watch(() => [props.path, props.qqSongId, session.revision, retry.value] as const, async ([path, qqSongId], _, cleanup) => {
  const controller = new AbortController(); cleanup(() => controller.abort());
  lines.value = []; plain.value = ""; failed.value = false;
  const url = coverUrl(path || null);
  if (!url && !qqSongId) { status.value = "暂无歌词"; return; }
  status.value = "正在加载歌词…";
  try {
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]);
    let text: string;
    if (qqSongId) text = (await qqMusic.lyrics(qqSongId, signal)).lyric;
    else {
      const response = await fetch(url!, { signal });
      if (!response.ok) throw new Error();
      text = await response.text();
    }
    if (controller.signal.aborted) return;
    lines.value = parseLyrics(text);
    if (!lines.value.length) plain.value = text;
    status.value = text.trim() ? "" : "暂无歌词";
  } catch { if (!controller.signal.aborted) { status.value = "歌词暂时无法加载，不影响播放"; failed.value = true; } }
}, { immediate: true });
watch(current, async () => {
  await nextTick();
  const row = container.value?.querySelector<HTMLElement>('[aria-current="true"]');
  if (row && container.value) container.value.scrollTo({ top: row.offsetTop - container.value.offsetTop - container.value.clientHeight / 2 + row.clientHeight / 2, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
});
</script>
<template><section class="lyrics-panel" aria-label="歌词"><p v-if="status" role="status">{{ status }}</p><button v-if="failed" type="button" @click="retry++">重新加载歌词</button><div v-if="!status && lines.length" ref="container" class="lyrics-scroll"><button v-for="(line, i) in lines" :key="i" :aria-current="current === i" :disabled="active === false || !player.duration" @click="player.seek(line.time)">{{ line.text || '♪' }}</button></div><pre v-else-if="!status">{{ plain }}</pre></section></template>
