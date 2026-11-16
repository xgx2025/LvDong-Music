# 律动音乐 · Vue 前端

基于 Vue 3、TypeScript、Vite、Vue Router 和 Pinia 的独立音乐前端。通过[独立后端](../backend/README.md)提供公开搜索，通过旧 Servlet 服务播放鉴权 HLS 媒体。

## 当前能力

- 公开歌曲与歌手搜索、分页、加载状态、空结果及错误重试。
- 播放队列去重、移除、结束后播放下一首。
- 应用级单 Audio 引擎，跨路由保留播放状态，支持进度、音量和播放器展开。
- HLS.js 按需加载，旧媒体地址支持部署 context；凭证仅保存在当前页面内存中。
- 响应式布局、键盘控制与减少动态效果偏好。

账号登录、收藏写入、评论、个人中心、歌词同步尚未接入。专辑和歌单独立搜索尚未实现，公开搜索中的收藏状态固定为 `false`。

## 运行与构建

需要 Node.js 22.12+ 和 npm，依赖版本由 lockfile 锁定。

```sh
cd frontend
npm ci
npm run dev
```

```sh
npm test
npm run build
```

构建会先执行 TypeScript 类型检查，产物在 `dist/`。

## 连接配置

可参考 [.env.example](.env.example) 创建本地配置。

| 配置 | 作用 |
| --- | --- |
| `API_PROXY_TARGET` | 开发服务器的 API 代理目标，默认 `http://127.0.0.1:8081` |
| `VITE_API_BASE` | 浏览器 API 根地址，默认使用同源地址 |
| `VITE_APP_BASE` | 静态资源部署前缀，默认 `/` |
| `VITE_LEGACY_BASE` | 旧 WAR 的完整部署根 URL，需包含 context |

使用 hash 路由。生产环境需独立配置静态托管和 API 反向代理，Vite 开发代理不会进入生产产物。跨域访问需服务端允许应用来源及 Authorization，HTTPS 页面应连接 HTTPS 媒体。

`VITE_` 配置会公开到浏览器，不应包含密钥或播放凭证。

## 播放边界

旧媒体适配使用现有 `processMp3`、`checkStatus`、`serveHls` 接口。未连接媒体服务时可搜索和整理队列；播放前需在“连接设置”提供有效旧站 Access Token。

清除凭证会停止播放并保留队列，但不等于服务端撤销会话。当前不提供登录或自动刷新。媒体准备最长等待五分钟，失败或超时后可重试；首次转换长音频可能需要较长时间。

HLS.js 向清单、切片和密钥请求发送 Bearer；原生 HLS 使用查询 token，依赖旧服务对子请求传播鉴权。真实旧站、Safari 原生 HLS 及实际部署环境仍需分别验证。
