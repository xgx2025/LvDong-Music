# 律动音乐 · Vue 前端

独立 Vue 3 + TypeScript + Vite + Vue Router + Pinia 应用。根目录 Servlet WAR 与 [独立 Boot 后端](../backend/README.md) 保留各自构建入口。

当前交付公开歌曲/歌手搜索、分页、请求加载/空态/错误重试、播放队列、音量/进度控制和可展开的常驻播放器。路由只替换页面区域，单一 Audio 引擎与播放状态由应用级 Store 持有。没有推荐假数据，也不将登录、评论、收藏写入、个人中心或歌词同步视为已迁移。

## 启动

需要 Node.js 22.12+（本轮验证 Node 24）和 npm。安装采用已提交 lockfile：

```sh
cd frontend
npm ci
npm run dev
```

默认访问终端显示的本地地址。开发 `/api` 代理至 `http://127.0.0.1:8081`，按后端 README 启动 Boot。可将 `.env.example` 复制为被忽略的 `.env.local` 修改配置。

| 配置 | 用途 |
| --- | --- |
| `API_PROXY_TARGET` | 开发时 Boot 代理目标，包含协议与端口；保留 `/api` 路径 |
| `VITE_API_BASE` | 浏览器请求根地址，默认空表示同源；跨域需服务端允许 CORS |
| `VITE_APP_BASE` | 静态部署前缀，默认 `/`；路由使用 hash，无需服务端 history 回退 |
| `VITE_LEGACY_BASE` | 可选旧 WAR 完整部署根 URL，包含 context，末尾 `/` |

Vite 配置在构建时注入。生产 `dist/` 需由静态服务器托管，并将同源 `/api` 转发给 Boot；开发代理不会自动出现在生产服务器中。所有 `VITE_` 变量均公开，禁止写入数据库密码、JWT 密钥或播放凭证。

## 搜索与旧媒体边界

搜索使用真实 `GET /api/v1/search`，按 `{code,message,data}` 解包；HTTP、业务、网络和格式错误分别处理。新请求取消前一请求，大整数 ID 保持字符串。此接口匿名，不发送身份凭证，专辑/歌单独立检索仍未实现。

封面绝对 HTTP(S) URL 保持不变；相对资源路径按 `VITE_LEGACY_BASE` 的部署 context 解析，不按 Vite 当前路由解析。未配置或图片失败时显示字形占位，不复制旧媒体。媒体服务跨域需允许应用来源及 Authorization 请求头，HTTPS 应用不能请求 HTTP 媒体。

旧站适配只使用已存在的 `hls?action=processMp3`（POST）、`checkStatus`（GET）和 `serveHls`。从“连接设置”输入有效旧站 Access Token，仅保留在本页内存，不读取旧 localStorage，不实现登录/刷新。切歌、取消准备、移除当前歌曲和清除凭证会取消请求并停止旧媒体。清除凭证仅清除本地状态，不等于服务端撤销会话。

任务排队/运行时轮询，最长等待五分钟；失败即时反馈，超时后可以重试。HLS.js 延迟加载，并向清单、切片、密钥请求发送 Bearer；原生 HLS 使用查询 token，依赖旧后端向子请求传播鉴权。生产应使用 HTTPS，并避免访问日志记录 token。凭证过期需重新配置，不能绕过认证。未配置服务时可以整理队列，播放会明确提示原因。

## 验证

```sh
npm test
npm run build
```

Vitest 覆盖错误分类、ID、取消、队列去重、快速切歌竞态、结束推进、移除当前、进度与音量边界、旧媒体任务排队与失败。构建先执行 `vue-tsc --noEmit`。HLS 独立 chunk 约 578 KB，当前仍产生体积提示，首次搜索无需加载它。

浏览器脚本需要 Python Playwright 与 Chromium：

```sh
python -m pip install playwright
python -m playwright install chromium
python tests/run_browser.py
```

也可通过 `PLAYWRIGHT_CHROMIUM_EXECUTABLE` 指定已安装 Chromium。脚本只监听本地，使用明确命名的搜索夹具和内存生成的 45 秒 WAV，验证真实音频播放、同一 Audio 跨路由且不新增 loadstart、暂停/恢复、键盘进度、展开/收起、错误重试、空态、390px 布局及移除当前歌曲。截图与日志保留在被忽略的 `test-results/`。

真实 Boot 搜索链路使用已打包 JAR 与独立内存 H2，不连接已有数据库：

```sh
python tests/run_browser.py --h2-jar <本机测试用H2-JAR路径>
```

先按后端说明打包 JAR；H2 使用后端测试依赖版本。该脚本显式覆盖所有数据源参数，创建最小内存夹具，验证 Vite→Boot→H2→页面、歌手搜索、审核过滤和加入队列；结束关闭进程。默认测试端口 5173/18081 被占用时拒绝运行。

本轮真实搜索链路与隔离 WAV 播放均通过；真实旧服务鉴权 HLS、Safari 原生 HLS、真实手机性能及完整账号业务尚未端到端验收。CSS 唱片装饰与字形占位为本应用原创，不引入外部图片/字体授权依赖。界面尊重 `prefers-reduced-motion`，动效只用于加载状态。

参见[迁移计划](../architecture/migration.md)与[验证记录](../architecture/verification.md)。
