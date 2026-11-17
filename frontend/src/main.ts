import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHashHistory } from "vue-router";
import App from "./App.vue";
import SearchView from "./views/SearchView.vue";
import QueueView from "./views/QueueView.vue";
import { useSessionStore } from "./stores/session";
import "font-awesome/css/font-awesome.css";
import "./style.css";
const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: "/", component: SearchView },
    { path: "/queue", component: QueueView },
    { path: "/settings", redirect: "/" },
    ...[
      ["service", "service-agreement.html", "服务条款"],
      ["privacy", "privacy-policy.html", "隐私政策"],
      ["musician", "musicianAgreement.html", "音乐人服务协议"],
      ["musician-privacy", "privacyPolicy.html", "音乐人隐私政策"],
    ].map(([kind, legacy, title]) => ({ path: `/agreements/${kind}`, alias: `/static/agreement/${legacy}`,
      component: () => import("./views/AgreementView.vue"), props: { kind }, meta: { public: true, dark: true, title } })),
    { path: "/login", component: () => import("./views/AuthView.vue"), meta: { public: true, dark: true, authLayout: true, title: "登录" } },
    { path: "/register", component: () => import("./views/AuthView.vue"), meta: { public: true, dark: true, authLayout: true, title: "注册" } },
    { path: "/password-reset", component: () => import("./views/AuthView.vue"), meta: { public: true, dark: true, authLayout: true, title: "重置密码" } },
    { path: "/profile", alias: "/user-profile.html", component: () => import("./views/ProfileView.vue"), meta: { dark: true, title: "个人中心" } },
    { path: "/musician", component: () => import("./views/MusicianView.vue"), meta: { dark: true, title: "音乐人申请" } },
    { path: "/upload", alias: "/upload-music.html", component: () => import("./views/UploadView.vue"), meta: { title: "上传音乐" } },
    { path: "/favorites", component: () => import("./views/FavoritesView.vue"), meta: { title: "我的收藏" } },
    { path: "/qq-playlists", component: () => import("./views/QqPlaylistsView.vue"), meta: { title: "QQ 歌单" } },
    { path: "/song/:id", component: () => import("./views/SongView.vue"), meta: { title: "歌曲详情" } },
    { path: "/recognition", alias: "/humming-recognition.html", component: () => import("./views/RecognitionView.vue"), meta: { title: "哼唱识曲" } },
    { path: "/feedback", alias: "/feedback.html", component: () => import("./views/FeedbackView.vue"), meta: { title: "意见反馈" } },
    { path: "/assistant", alias: "/ai-assistant.html", component: () => import("./views/AssistantView.vue"), meta: { title: "AI助手" } },
    { path: "/home.html", redirect: "/" },
    { path: "/search.html", redirect: to => ({ path: "/", query: to.query }) },
    { path: "/login.html", redirect: "/login" },
    { path: "/register.html", redirect: "/register" },
    { path: "/password-reset.html", redirect: "/password-reset" },
    { path: "/comment.html", redirect: to => ({ path: `/song/${String(to.query.songId || to.query.resourceId || '')}` }) },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});
const pinia = createPinia();
let restored: Promise<void> | undefined;
router.beforeEach(async (to) => {
  const session = useSessionStore(pinia);
  await (restored ??= session.restore());
  // All site pages require login unless explicitly marked as an account/legal entry.
  if (to.meta.public !== true && !session.authenticated)
    return { path: "/login", query: { redirect: to.fullPath } };
});
router.afterEach((to) => {
  document.title = `${to.meta.title || (to.path === "/queue" ? "播放队列" : "发现音乐")} · 律动音乐`;
});
const app = createApp(App).use(pinia).use(router);
// Finish session restoration and the first route guard before rendering any site UI.
void router.isReady().then(() => app.mount("#app"));
