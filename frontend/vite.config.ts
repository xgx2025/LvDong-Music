import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  return {
    base: env.VITE_APP_BASE || "/",
    plugins: [vue()],
    server: {
      proxy: {
        "/api": {
          target: env.API_PROXY_TARGET || "http://127.0.0.1:8081",
          changeOrigin: true,
        },
      },
    },
  };
});
