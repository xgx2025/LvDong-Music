<script setup lang="ts">
import { reactive, ref } from "vue";
const form = reactive({ type: "功能建议", content: "", contact: "" });
const notice = ref("");
function download() {
  const blob = new Blob([JSON.stringify({ ...form, createdAt: new Date().toISOString() }, null, 2)], { type: "application/json;charset=utf-8" });
  const url = URL.createObjectURL(blob), link = document.createElement('a');
  link.href = url; link.download = '律动音乐反馈.json'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  notice.value = "反馈已导出到本机，尚未发送给站点";
}
</script>
<template><section class="content-page"><h1>意见反馈</h1><p class="subtle">你的建议，让律动变得更好</p><form class="settings-card form-stack" @submit.prevent="download"><label>反馈类型<select v-model="form.type"><option>功能建议</option><option>问题反馈</option><option>版权问题</option><option>其他</option></select></label><label>反馈内容<textarea v-model="form.content" required minlength="5" maxlength="2000" rows="7" placeholder="请详细描述你的问题或建议…"></textarea></label><label>联系方式（可选）<input v-model="form.contact" maxlength="200" /></label><p class="subtle">本站目前未提供反馈接收接口。你可以先导出反馈文件保存，文件不会自动发送。</p><button class="primary" type="submit">导出反馈</button><p v-if="notice" role="status">{{ notice }}</p></form></section></template>
