import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  return {
    base: env.VITE_APP_BASE || "/",
    plugins: [vue()],
    server: {
      proxy: {
        "/legacy": {
          target: env.LEGACY_PROXY_TARGET || "http://127.0.0.1:8080/xgxmusic",
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/legacy/, ""),
        },
        "/api": {
          target: env.API_PROXY_TARGET || "http://127.0.0.1:8081",
          changeOrigin: true,
          ws: true,
        },
      },
    },
  };
});
