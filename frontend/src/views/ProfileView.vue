<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useSessionStore } from "../stores/session";
import { updateProfile, musicianStatus, readFile, getProfile, getFavorites } from "../api/account";
import { errorText } from "../api/servlet";
import { coverUrl } from "../api/legacy";
const session = useSessionStore(), router = useRouter();
const dialog = ref<HTMLDialogElement>(), avatarInput = ref<HTMLInputElement>();
const avatarReading = ref(false);
let avatarGeneration = 0;
onBeforeUnmount(() => avatarGeneration++);
function closed() { avatarGeneration++; avatarReading.value = false; }
function close() { if (!busy.value) dialog.value?.close(); }
function backdrop(event: MouseEvent) {
  if (event.target !== dialog.value) return;
  const rect = dialog.value.getBoundingClientRect();
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) close();
}
const status = ref(""), statusError = ref(""), busy = ref(false), error = ref(""), notice = ref("");
const favoriteCount = ref<number>();
const draft = reactive({ username: "", gender: "未知", bio: "", avatarBase64: "" });
const avatar = computed(() => coverUrl(session.user?.avatar || null));
async function loadStatus() {
  statusError.value = "";
  try { status.value = (await musicianStatus()).status; }
  catch (e) { statusError.value = errorText(e); }
}
onMounted(async () => {
  void loadStatus();
  void getFavorites(1).then(data => { favoriteCount.value = data.favorites.total; }).catch(() => {});
  try { session.updateUser((await getProfile()).user); }
  catch (e) { error.value = errorText(e); }
});
function edit() {
  Object.assign(draft, { username: session.user?.username || "", gender: session.user?.gender || "未知", bio: session.user?.bio || "", avatarBase64: "" });
  avatarGeneration++; avatarReading.value = false;
  if (avatarInput.value) avatarInput.value.value = "";
  error.value = ""; dialog.value?.showModal();
}
async function avatarChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const version = ++avatarGeneration;
  avatarReading.value = true; error.value = "";
  try { const result = await readFile(file, "image"); if (version === avatarGeneration) draft.avatarBase64 = result.data; }
  catch (e) { if (version === avatarGeneration) { error.value = errorText(e); (event.target as HTMLInputElement).value = ""; } }
  finally { if (version === avatarGeneration) avatarReading.value = false; }
}
async function save() {
  if (busy.value || avatarReading.value) return;
  error.value = ""; busy.value = true;
  try {
    const result = await updateProfile({ ...draft, username: draft.username.trim() });
    session.updateUser({ username: draft.username.trim(), gender: draft.gender, bio: draft.bio, ...(result.avatarUrl ? { avatar: result.avatarUrl } : {}) });
    notice.value = "资料更新成功"; dialog.value?.close();
  } catch (e) { error.value = errorText(e); }
  finally { busy.value = false; }
}
async function logout() {
  try { await session.logout(); await router.replace("/login"); }
  catch { await router.replace({ path: "/login", query: { reason: "logout-failed" } }); }
}
const date = computed(() => {
  const value = session.user?.registerTime;
  if (!value) return "未知";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleDateString("zh-CN");
});
</script>
<template>
  <section v-if="session.user" class="profile-page">
    <div class="profile-card">
      <div class="profile-status"><span class="pill">{{ status === 'approved' ? '认证音乐人' : status === 'pending' ? '审核中' : status === 'rejected' ? '申请未通过' : status === 'normal' ? '普通用户' : '资格待确认' }}</span></div>
      <div class="profile-heading"><h2>个人资料</h2><p>管理你的个人信息和账户设置</p></div>
      <p v-if="error && !dialog?.open" role="alert" class="form-error">{{ error }}</p>
      <div class="profile-identity"><div class="profile-avatar"><img v-if="avatar" :src="avatar" alt="用户头像" /><span v-else>{{ session.user.username.slice(0, 1) }}</span></div><div><h3>{{ session.user.username }}</h3><p>{{ session.user.email }}</p><div class="profile-badges"><span>注册时间: {{ date }}</span><RouterLink to="/favorites">收藏歌曲: {{ favoriteCount ?? "—" }}</RouterLink><span>{{ session.user.gender || '未设置' }}</span></div></div></div>
      <div class="profile-bio"><h4>个人简介</h4><p>{{ session.user.bio || '该用户尚未填写个人简介' }}</p></div>
      <dl class="profile-details"><dt>用户ID</dt><dd>{{ session.user.id }}</dd><dt>邮箱</dt><dd>{{ session.user.email }}</dd><dt>用户名</dt><dd>{{ session.user.username }}</dd><dt>性别</dt><dd>{{ session.user.gender || '未设置' }}</dd></dl>
      <p v-if="statusError" class="form-error" role="alert">{{ statusError }} <button @click="loadStatus">重试资格查询</button></p>
      <p v-if="notice" role="status">{{ notice }}</p>
      <div class="profile-actions"><button @click="logout">退出登录</button><RouterLink class="orange-button" to="/musician">{{ status === 'approved' ? '音乐人资料' : '申请成为音乐人' }}</RouterLink><button class="primary" @click="edit">编辑资料</button></div>
      <div class="profile-shortcuts"><RouterLink to="/favorites">我的收藏</RouterLink><RouterLink to="/upload">上传音乐</RouterLink></div>
    </div>
    <dialog ref="dialog" class="profile-dialog" aria-labelledby="edit-profile-title" @click="backdrop" @close="closed" @cancel="busy && $event.preventDefault()">
      <button class="dialog-close" type="button" aria-label="关闭资料编辑" :disabled="busy" @click="close">×</button>
      <form class="form-stack" @submit.prevent="save"><div class="dialog-heading"><h2 id="edit-profile-title">编辑个人资料</h2><p>更新你的个人信息</p></div>
        <div class="profile-avatar-editor"><div class="profile-avatar"><img v-if="draft.avatarBase64 || avatar" :src="draft.avatarBase64 || avatar!" alt="头像预览" /><span v-else>{{ draft.username.slice(0, 1) }}</span></div><label class="avatar-upload-control">更换头像<input ref="avatarInput" type="file" class="sr-only" aria-label="头像" accept="image/png,image/jpeg,image/webp,image/gif" :disabled="busy" @change="avatarChange" /></label><small>{{ avatarReading ? '正在读取头像…' : '支持PNG、JPG、WebP或GIF，最大3MB' }}</small></div>
        <label>用户名<input v-model="draft.username" required minlength="2" maxlength="20" :disabled="busy" /></label>
        <label>性别<select v-model="draft.gender" :disabled="busy"><option>未知</option><option>男</option><option>女</option></select></label>
        <label>个人简介<textarea aria-label="个人简介" v-model="draft.bio" maxlength="200" rows="4" :disabled="busy"></textarea><small>{{ draft.bio.length }}/200</small></label>
        <p v-if="error" role="alert" class="form-error">{{ error }}</p>
        <div class="button-group"><button type="button" :disabled="busy" @click="close">取消</button><button class="primary" :disabled="busy || avatarReading" type="submit">{{ busy ? '保存中…' : '保存资料' }}</button></div>
      </form>
    </dialog>
  </section>
</template>
