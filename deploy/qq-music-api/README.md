# QQ 音乐账号连接服务

本站的 QQ 音乐功能依赖固定版本 `@yakult-green-tea/qq-music-api@3.3.6`。它是[非官方 QQ 音乐 API](https://github.com/yakult-green-tea/qq-music-api)，按每个用户自己的扫码会话连接。支持账号连接、歌单读取与导入、加载播放队列和标准音质播放；QQ 曲库搜索尚未接入。

## 直接运行 Node.js（本地推荐）

需要 Node.js 24+。在本目录执行：

```sh
npm ci --ignore-scripts
npm run setup
npm start
```

`setup` 为本服务生成本地 `.env` 和随机密钥，并为已有 `backend/.env` 补齐 `QQ_MUSIC_API_BASE`、`QQ_MUSIC_CONNECTION_KEY`，保留已有非空配置和密钥。首次使用仍需按下节执行增量建表脚本。

`npm start` 自动加载本目录 `.env`，只监听 `127.0.0.1:3200`。设备身份和加密会话保存在本目录 `.auth-state/`，该目录及 `.env` 已被 Git 忽略；跨重启保留它们。可以用 `QQ_API_PORT` 修改端口，配置的 Boot API 地址也应与之对应。终端运行期间服务保持可用，按 Ctrl+C 停止。

可从项目根目录运行 `npm --prefix deploy/qq-music-api start`。Spring Boot 与 Vue 仍分别启动。

## Docker Compose（可选）

需要 Docker Compose。复制本目录 `.env.example` 为 `.env`，为 `QQ_SESSION_SECRET` 填入至少32字节随机密钥。可以执行两次下面的命令生成两个不同的值，分别用于 QQ 服务和 Boot 的绑定加密：

```sh
node -e "console.log(require('node:crypto').randomBytes(32).toString('base64'))"
```

在本目录运行：

```sh
docker compose up -d --build
```

服务只映射到本机 `127.0.0.1:3200`，不要把 QQ API 直接暴露给浏览器或公网。设备身份与加密会话保存在具名卷中；重建容器时保留卷和密钥。

## 配置本站后端

1. 对已有业务库显式执行 [006_qq_music_connection.sql](../../backend/src/main/resources/db/006_qq_music_connection.sql) 和 [007_qq_playlist.sql](../../backend/src/main/resources/db/007_qq_playlist.sql)。已执行过的脚本无需重跑，应用不会自动建表。
2. 在 `backend/.env` 配置：

```properties
QQ_MUSIC_API_BASE=http://127.0.0.1:3200
QQ_MUSIC_CONNECTION_KEY=<另一个Base64编码的32字节随机密钥>
```

3. 重启 Boot 和 Vue。登录本站，进入个人中心的“连接 QQ 音乐”，点击“连接账号”，用手机扫描并确认。

## 导入与加载歌单

连接后进入导航栏的“QQ歌单”，或从个人中心点击“管理 QQ 歌单”：

1. 在“QQ 账号歌单”查看“我喜欢”、自建和收藏的歌单，选择歌单后分页读取歌曲。
2. 点击“导入本站”，后端完整读取歌曲后，将元数据保存为本人私有快照；同一站内用户、QQ 账号和源歌单再次导入会更新同一快照。
3. 在“已导入本站”查看保存的内容，可更新导入、加载完整歌单到播放队列或播放全部。移除只删除本站快照，不改变 QQ 音乐原歌单。

单个歌单最多导入或一次加载5000首。遇到空续页、重复页、上游失败或保存失败时，不覆盖旧快照。快照不包含音频文件、QQ 凭证或临时播放 URL；断开 QQ 连接后仍能查看已保存的歌曲信息，播放和重新导入需要有效连接。

播放前由 Boot 使用当前用户绑定的 QQ 会话取临时音频地址，浏览器从 QQ CDN 播放，不经过本地 HLS 转码。第一版使用标准音质；完整音频不可用时尝试试听，播放器会明确提示试听片段。实际可播放性取决于平台返回、账号权限及地区。

Boot 绑定加密密钥、QQ 服务密钥和 QQ 会话文件应跨重启保持一致。更换绑定加密密钥后，已有绑定需要重新扫码；QQ 服务丢失会话文件时也需要重连。QQ 凭证、音乐 session token 都不发送到本站浏览器，也不进入本站 URL 或日志。

如 Boot 运行在容器内，API 地址应改为该容器可达的内网服务名，保持 QQ 服务端口只在内网可达。此 Compose 示例面向 Boot 运行在宿主机的情况。

## 站内接口

接口前缀 `/api/v1/music-providers/qq`，全部校验本站 Bearer 会话，返回 `Result<T>`，并设置 `Cache-Control: no-store`。

| 方法和路径 | 功能 |
| --- | --- |
| `GET /capabilities` | 返回已配置状态及支持的扫码方式 |
| `GET /connection` | 校验当前用户的音乐账号连接 |
| `POST /login` | `{channel:"qqmusic"}` 创建本站扫码任务 |
| `POST /login/{id}/check` | 检查本人任务并在确认后保存绑定 |
| `DELETE /login/{id}` | 取消本人任务，不删除已确认的绑定 |
| `DELETE /connection` | 删除本人绑定并尽力撤销上游会话 |
| `GET /playlists?favoriteOffset=0` | 当前 QQ 账号歌单及收藏续读位置 |
| `GET /playlists/{id}/songs?offset=0&limit=100` | 本人账号歌单的歌曲分页，`liked` 代表我喜欢 |
| `POST /imports` | `{playlistId:"..."}` 完整导入或更新快照 |
| `GET /imports` | 本站当前用户的导入列表 |
| `GET /imports/{id}/songs?offset=0&limit=100` | 本人已导入歌单的歌曲分页 |
| `DELETE /imports/{id}` | 删除本站快照 |
| `POST /playback` | `{songMid:"...",mediaMid:"..."}` 解析当前账号可用的音频地址 |

浏览器只能取得本站任务 UUID 和嵌入式二维码图像。任务最长3分钟，每位用户一个，实例最多256个。站内任务轮询间隔至少1.5秒，失败后界面暂停并提供显式重试。断开连接会立即删除本站访问绑定；上游不可达时，其会话撤销不能保证立即完成。

当前接法只支持常驻服务的 `stored` 会话。Boot 使用进程内扫码任务和按用户串行处理，部署应保持单实例；多实例接入需要另行共享任务、分布式互斥及明确上游会话路由。

## 验证

```sh
mvn -f backend/pom.xml verify
```

项目根目录运行后端验证；QQ 连接测试使用本机模拟上游和隔离 H2，覆盖身份校验、跨用户任务隔离、加密持久化、取消与解绑、上游故障及无效二维码。测试不会登录真实 QQ 账号。真实扫码仍受网络、设备上限和平台风控影响。
