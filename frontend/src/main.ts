import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHashHistory } from "vue-router";
import App from "./App.vue";
import SearchView from "./views/SearchView.vue";
import QueueView from "./views/QueueView.vue";
import SettingsView from "./views/SettingsView.vue";
import "./style.css";
const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", component: SearchView },
    { path: "/queue", component: QueueView },
    { path: "/settings", component: SettingsView },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});
router.afterEach((to) => {
  document.title = `${to.path === "/queue" ? "播放队列" : to.path === "/settings" ? "连接设置" : "发现音乐"} · 律动音乐`;
});
createApp(App).use(createPinia()).use(router).mount("#app");
