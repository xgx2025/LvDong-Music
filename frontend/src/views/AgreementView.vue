<script setup lang="ts">
import service from "../content/agreements/service.html?raw";
import privacy from "../content/agreements/privacy.html?raw";
import musician from "../content/agreements/musician.html?raw";
import musicianPrivacy from "../content/agreements/musician-privacy.html?raw";
defineProps<{ kind: string }>();
// These documents are fixed, reviewed source assets, never server or user HTML.
const documents: Record<string, string> = { service, privacy, musician, "musician-privacy": musicianPrivacy };
</script>
<template>
  <section class="content-page agreement-page">
    <article class="agreement-document" v-html="documents[kind]"></article>
    <RouterLink to="/">返回首页</RouterLink>
  </section>
</template>
