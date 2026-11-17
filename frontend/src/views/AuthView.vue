<script setup lang="ts">
import AppIcon from "../components/AppIcon.vue";
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useSessionStore } from "../stores/session";
import { sendCode, register, resetPassword } from "../api/account";
import { errorText } from "../api/servlet";
const route = useRoute(), router = useRouter(), session = useSessionStore();
const authLink = (path: string) => ({ path, query: route.query.redirect ? { redirect: String(route.query.redirect) } : {} });
const mode = computed(() => route.path === "/register" ? "register" : route.path === "/password-reset" ? "reset" : "login");
const title = computed(() => ({ login: "登录", register: "注册账号", reset: "重置密码" })[mode.value]);
const form = reactive({ username: "", email: "", password: "", confirm: "", verificationCode: "", remember: false });
const busy = ref(false), sending = ref(false), remaining = ref(0), visible = ref(false), error = ref(""), notice = ref("");
const termsAccepted = ref(false), confirmVisible = ref(false);
const passwordMismatch = computed(() => mode.value !== "login" && !!form.confirm && form.password !== form.confirm);
let timer: ReturnType<typeof setInterval> | undefined;
let generation = 0, codeGeneration = 0;
watch(() => [mode.value, form.email.trim()], () => {
  codeGeneration++; clearInterval(timer); remaining.value = 0; sending.value = false;
  form.verificationCode = ""; notice.value = "";
});
watch(mode, () => { generation++; error.value = notice.value = form.password = form.confirm = form.verificationCode = ""; busy.value = false; termsAccepted.value = false; visible.value = confirmVisible.value = false; });
onBeforeUnmount(() => { generation++; codeGeneration++; clearInterval(timer); });
async function code() {
  if (sending.value || remaining.value) return;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { error.value = "请输入有效邮箱"; return; }
  const version = ++codeGeneration;
  sending.value = true; error.value = "";
  try {
    await sendCode(form.email.trim()); if (version !== codeGeneration) return; notice.value = "验证码已发送，请检查邮箱";
    remaining.value = 60;
    timer = setInterval(() => { if (--remaining.value <= 0) clearInterval(timer); }, 1000);
  } catch (e) { if (version === codeGeneration) error.value = errorText(e); }
  finally { if (version === codeGeneration) sending.value = false; }
}
async function submit() {
  if (busy.value) return;
  error.value = notice.value = "";
  if (mode.value === "register" && !termsAccepted.value) { error.value = "请先阅读并同意服务条款和隐私政策"; return; }
  if (mode.value !== "login" && form.password !== form.confirm) { error.value = "两次输入的密码不一致"; return; }
  busy.value = true;
  const version = generation, currentMode = mode.value;
  try {
    if (currentMode === "login") {
      await session.login(form.email.trim(), form.password, form.remember);
      if (version !== generation) return;
      const redirect = String(route.query.redirect || "/");
      await router.replace(redirect.startsWith("/") && !redirect.startsWith("//") && !redirect.includes(":") ? redirect : "/");
    } else {
      const data = { username: form.username.trim(), email: form.email.trim(), password: form.password, newPassword: form.password, verificationCode: form.verificationCode.trim() };
      await (currentMode === "register" ? register(data) : resetPassword(data));
      if (version !== generation) return;
      form.password = form.confirm = form.verificationCode = "";
      await router.replace({ path: "/login", query: { ...authLink("/login").query, completed: currentMode } });
    }
  } catch (e) { if (version === generation) error.value = errorText(e); }
  finally { if (version === generation) { busy.value = false; form.password = ""; } }
}
</script>
<template>
  <section class="auth-page">
    <div class="auth-brand"><span class="brand-mark" aria-hidden="true"><AppIcon name="music" /></span><h1>律动</h1><p>沉浸式音乐体验</p></div>
    <div class="auth-card">
      <h2 v-if="mode !== 'login'">{{ title }}</h2>
      <p v-if="route.query.completed" class="notice" role="status">{{ route.query.completed === 'register' ? '注册成功，请登录' : '密码已重置，请重新登录' }}</p>
      <p v-if="route.query.reason === 'logout-failed'" class="form-error" role="alert">本机登录已清除，但服务端退出请求失败。请重试登录后退出，以撤销原会话。</p>
      <form class="form-stack" @submit.prevent="submit">
        <label v-if="mode === 'register'"><span class="field-label"><AppIcon name="user" />用户名</span><input v-model="form.username" required minlength="2" maxlength="20" autocomplete="username" placeholder="请输入用户名" /></label>
        <label><span class="field-label"><AppIcon name="mail" />邮箱</span><input v-model="form.email" type="email" required maxlength="254" autocomplete="email" placeholder="example@mail.com" /></label>
        <div v-if="mode === 'reset'" class="verification-field">
          <label><span class="field-label"><AppIcon name="shield" />验证码</span><input v-model="form.verificationCode" required autocomplete="one-time-code" maxlength="10" placeholder="请输入邮箱验证码" /></label>
          <button type="button" :disabled="sending || remaining > 0" @click="code">{{ remaining ? `${remaining}秒后重发` : sending ? '发送中…' : '获取验证码' }}</button>
        </div>
        <div class="password-field">
          <div class="form-inline"><label for="account-password"><AppIcon name="lock" />{{ mode === 'reset' ? '新密码' : '密码' }}</label><RouterLink v-if="mode === 'login'" :to="authLink('/password-reset')">忘记密码?</RouterLink></div>
          <div class="password-input"><input id="account-password" v-model="form.password" :type="visible ? 'text' : 'password'" required :minlength="mode === 'login' ? undefined : 8" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="请输入密码" /><button type="button" :aria-label="visible ? '隐藏密码' : '显示密码'" :aria-pressed="visible" @click="visible = !visible"><AppIcon :name="visible ? 'eye' : 'eye-off'" /></button></div>
        </div>
        <p v-if="mode !== 'login'" class="password-hint">密码至少8个字符</p>
        <div v-if="mode !== 'login'" class="password-field"><label for="account-confirm"><AppIcon name="lock" />确认密码</label><div class="password-input"><input id="account-confirm" v-model="form.confirm" :type="confirmVisible ? 'text' : 'password'" required minlength="8" autocomplete="new-password" placeholder="再次输入密码" :aria-invalid="passwordMismatch || undefined" :aria-describedby="passwordMismatch ? 'password-mismatch' : undefined" /><button type="button" :aria-label="confirmVisible ? '隐藏确认密码' : '显示确认密码'" :aria-pressed="confirmVisible" @click="confirmVisible = !confirmVisible"><AppIcon :name="confirmVisible ? 'eye' : 'eye-off'" /></button></div><small v-if="passwordMismatch" id="password-mismatch" class="form-error">两次输入的密码不一致</small></div>
        <div v-if="mode === 'register'" class="verification-field">
          <label><span class="field-label"><AppIcon name="shield" />验证码</span><input v-model="form.verificationCode" required autocomplete="one-time-code" maxlength="10" placeholder="请输入邮箱验证码" /></label>
          <button type="button" :disabled="sending || remaining > 0" @click="code">{{ remaining ? `${remaining}秒后重发` : sending ? '发送中…' : '获取验证码' }}</button>
        </div>
        <label v-if="mode === 'login'" class="checkbox-label"><input v-model="form.remember" type="checkbox" />在当前标签页保持登录</label>
        <label v-if="mode === 'register'" class="checkbox-label"><input v-model="termsAccepted" type="checkbox" required /><span>我已阅读并同意<RouterLink to="/agreements/service" target="_blank" rel="noopener">《服务条款》</RouterLink>和<RouterLink to="/agreements/privacy" target="_blank" rel="noopener">《隐私政策》</RouterLink></span></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
        <button class="primary auth-submit" :disabled="busy" type="submit">{{ busy ? '处理中…' : title }}</button>
      </form>
      <template v-if="mode === 'login'"><div class="auth-divider">或者</div><div class="auth-social" aria-describedby="oauth-unavailable"><button type="button" disabled aria-label="QQ登录暂未接入"><AppIcon name="qq" /><span class="sr-only">QQ</span></button><button type="button" disabled aria-label="微信登录暂未接入"><AppIcon name="wechat" /><span class="sr-only">微信</span></button><button type="button" disabled aria-label="GitHub登录暂未接入"><AppIcon name="github" /><span class="sr-only">GitHub</span></button></div><p id="oauth-unavailable" class="auth-social-note">第三方登录暂未接入</p></template>
      <p class="auth-links"><RouterLink v-if="mode === 'login'" :to="authLink('/register')">还没有账号？立即注册</RouterLink><RouterLink v-else :to="authLink('/login')">返回登录</RouterLink></p>
    </div>
    <RouterLink v-if="session.authenticated" class="auth-back" to="/">返回首页</RouterLink>
  </section>
</template>
