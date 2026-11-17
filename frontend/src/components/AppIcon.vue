<script setup lang="ts">
defineProps<{ name: string }>();
const paths: Record<string, string> = {
  previous: 'M5 5v14M19 5 8 12l11 7V5Z',
  next: 'M19 5v14M5 5l11 7-11 7V5Z',
  play: 'm8 5 11 7-11 7V5Z',
  pause: 'M8 5v14M16 5v14',
  stop: 'M6 6h12v12H6Z',
  home: 'm3 10 9-7 9 7v10h-7v-6h-4v6H3V10Z',
  microphone: 'M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0V5ZM5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8',
  upload: 'M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6',
  chat: 'M4 4h16v12H9l-5 4V4Z',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2',
  music: 'M9 18V5l11-2v13M9 8l11-2M9 18a3 2 0 1 1-3-2 3 2 0 0 1 3 2Zm11-2a3 2 0 1 1-3-2 3 2 0 0 1 3 2Z',
};
</script>
<template><svg class="app-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] || paths.music" /></svg></template>
