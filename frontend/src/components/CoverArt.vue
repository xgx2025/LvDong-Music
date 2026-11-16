<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { coverUrl } from "../api/legacy";
const props = defineProps<{ path?: string | null; name?: string | null }>();
const failed = ref(false);
const url = computed(() => coverUrl(props.path || null));
watch(url, () => {
  failed.value = false;
});
</script>
<template>
  <span class="cover-art"
    ><img
      v-if="url && !failed"
      :src="url"
      alt=""
      loading="lazy"
      referrerpolicy="no-referrer"
      @error="failed = true"
    /><span v-else class="cover-fallback" aria-hidden="true">{{
      (name || "律").slice(0, 1)
    }}</span></span
  >
</template>
