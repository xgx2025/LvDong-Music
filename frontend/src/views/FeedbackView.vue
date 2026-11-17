<script setup lang="ts">
import AppIcon from "../components/AppIcon.vue";
import { onBeforeUnmount, reactive, ref } from "vue";
const form = reactive({ type: "错误报告", content: "", contact: "" });
const notice = ref(""), error = ref(""), screenshot = ref(""), filename = ref(""), reading = ref(false);
const fileInput = ref<HTMLInputElement>();
let reader: FileReader | undefined;
function remove() { reader?.abort(); screenshot.value = filename.value = ""; reading.value = false; if (fileInput.value) fileInput.value.value = ""; }
function select(file?: File) {
  remove(); error.value = "";
  if (!file) return;
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) { error.value = "请选择5MB以内的JPG或PNG截图"; return; }
  reading.value = true;
  const current = new FileReader(); reader = current;
  current.onload = () => { screenshot.value = String(current.result); filename.value = file.name; reading.value = false; };
  current.onerror = () => { error.value = "截图读取失败，请重新选择"; reading.value = false; };
  current.readAsDataURL(file);
}
onBeforeUnmount(() => reader?.abort());
function download() {
  if (reading.value) return;
  const blob = new Blob([JSON.stringify({ ...form, screenshot: screenshot.value || undefined, filename: filename.value || undefined, createdAt: new Date().toISOString() }, null, 2)], { type: "application/json;charset=utf-8" });
  const url = URL.createObjectURL(blob), link = document.createElement('a');
  link.href = url; link.download = '律动音乐反馈.json'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  notice.value = "反馈已导出到本机，尚未发送给站点";
}
const faqs = [
  ['反馈提交后多久能得到回复？', '当前仅支持导出反馈到本机，尚未连接反馈接收服务，因此不会自动收到站点回复。'],
  ['为什么我的建议没有被采纳？', '建议是否采纳需要结合使用场景与产品计划评估。描述具体的问题和预期效果，更有助于理解你的建议。'],
  ['如何查看我的反馈处理进度？', '当前没有在线处理进度查询。请保存导出的反馈文件，方便之后联系站点时提供。'],
  ['提交反馈时需要注意什么？', '请描述操作步骤、遇到的问题和预期结果；截图中请勿包含密码或其他敏感信息。'],
];
</script>
<template>
  <section class="feedback-page"><header class="centered-heading"><h1>意见反馈</h1><p>你的每一条建议，都是我们进步的动力。帮助我们打造更好的音乐体验</p></header>
    <form class="settings-card form-stack feedback-form" @submit.prevent="download">
      <fieldset class="feedback-types"><legend>反馈类型 *</legend><div><label v-for="type in ['错误报告', '功能建议', '投诉', '其他']" :key="type"><input v-model="form.type" type="radio" name="feedback-type" :value="type" /><span><AppIcon :name="type === '错误报告' ? 'bug' : type === '功能建议' ? 'bulb' : type === '投诉' ? 'warning' : 'help'" />{{ type }}</span></label></div></fieldset>
      <label>反馈内容<textarea aria-label="反馈内容" v-model="form.content" required minlength="5" maxlength="2000" rows="6" placeholder="请详细描述你的问题或建议…"></textarea><small class="character-count">{{ form.content.length }}/2000</small></label>
      <label>联系方式（可选）<input v-model="form.contact" maxlength="200" placeholder="邮箱/电话/QQ（选填，方便联系）" /></label>
      <div><label for="feedback-image">上传截图</label><label class="file-drop" for="feedback-image" @dragover.prevent @drop.prevent="select($event.dataTransfer?.files[0])"><AppIcon name="cloud-upload" /><strong>点击或拖拽文件到此处</strong><small>支持JPG/PNG格式，最大5MB</small><input id="feedback-image" ref="fileInput" class="sr-only" type="file" accept="image/jpeg,image/png" @change="select(($event.target as HTMLInputElement).files?.[0])" /></label><div v-if="screenshot" class="screenshot-preview"><img :src="screenshot" alt="反馈截图预览" /><span>{{ filename }}</span><button type="button" @click="remove"><AppIcon name="trash" />移除</button></div></div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p><p class="subtle">本站目前未提供反馈接收接口。你可以先导出反馈文件保存，文件不会自动发送。</p><button class="primary" :disabled="reading" type="submit"><AppIcon :name="reading ? 'spinner' : 'save'" />{{ reading ? '截图读取中…' : '导出反馈' }}</button><p v-if="notice" role="status">{{ notice }}</p>
    </form>
    <section class="feedback-faq"><h2><AppIcon name="help" />常见问题</h2><details v-for="item in faqs" :key="item[0]"><summary>{{ item[0] }}</summary><p>{{ item[1] }}</p></details></section>
  </section>
</template>
