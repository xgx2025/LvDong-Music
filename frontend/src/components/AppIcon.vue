<script setup lang="ts">
import { computed, inject } from "vue";
import { iconClasses, type IconName } from "./icons";
const props = withDefaults(defineProps<{ name: IconName; filled?: boolean }>(), { filled: false });
// The original profile and creator dialogs used Font Awesome 6.0.0.
const inheritedVersion = inject<number>("original-icon-version", 4);
const version = computed(() => props.name === "robot" || props.name === "guitar" ? 6 : inheritedVersion);
const iconClass = computed(() => props.name === "heart" && props.filled ? "fa-heart" : iconClasses[props.name]);
</script>
<template>
  <i class="app-icon fa" :class="[iconClass, { 'font-awesome-6': version === 6, 'icon-spin': name === 'spinner' }]" :data-icon="name" :data-icon-version="version" aria-hidden="true"></i>
</template>
