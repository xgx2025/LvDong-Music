# 律动音乐 · 独立后端

基于 Java 21 / Spring Boot 4.1.2 的分层单体，提供搜索、持久会话、账号写入、收藏、歌曲详情、评论、资料编辑、音乐人申请、上传、歌词识曲与加密 HLS。旧 Servlet WAR 保留独立构建入口，Vue 音乐业务已全部切换到新服务。

## 当前能力

- `GET /api/v1/search`：公开搜索审核通过的歌曲及相关歌手。
- `POST /searchServlet?action=search`：兼容旧 JSON 搜索请求。
- Controller → Service → JDBC DAO 分层，Service 管理查询快照与写入事务。
- 参数校验、统一异常响应、字符串 ID 及当前页歌手批量查询。
- 已登录用户的 QQ 音乐扫码连接、加密账号绑定、歌单读取与导入快照、歌曲分页和标准音质播放地址解析；配置方式见[QQ 音乐服务](../deploy/qq-music-api/README.md)，启用前显式执行 `006_qq_music_connection.sql` 和 `007_qq_playlist.sql`。QQ 曲库搜索尚未接入。

登录、刷新、退出和本人资料使用持久会话；其余账号写入、评论、文件业务与媒体也已迁入 Boot。搜索统一按匿名处理，`userHasFavorite=false`；专辑和歌单结果暂为空。

## 构建与运行

需要 Java 21、Maven，以及具有现有业务表的 MySQL 数据库。

首次启动前，必须在目标业务库执行增量脚本 `003_auth_sessions.sql`、`004_email_verifications.sql`、`005_musician_materials.sql`（位于 `src/main/resources/db/`）。仅填写环境变量不会创建这些表；缺少会话表时，即使密码正确也无法完成登录。不要重新导入初始化 SQL 覆盖已有数据。

首次使用时，将 [`.env.example`](.env.example) 复制为 `backend/.env`，填写 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，`PORT` 可选（默认8081）。本地 `.env` 被 Git 忽略，不会打入 JAR，示例文件可随源码提交。

从仓库根目录或 `backend/` 目录启动时会自动加载该文件。文件按 Java properties 的 `KEY=value` 格式填写，不加引号、不使用 `export`，反斜杠需转义。系统环境变量优先于文件；若从仓库根目录启动且根目录也有 `.env`，以 `backend/.env` 的同名值为准。

```sh
mvn -f backend/pom.xml clean verify
java -jar backend/target/lvdong-backend-0.1.0-SNAPSHOT.jar
```

也可直接通过系统环境变量或 Spring 标准外部配置提供参数。新服务不加载旧站的 `APP_CONFIG_FILE`，不自动建表或执行升级脚本。

## 搜索接口

```text
GET /api/v1/search?keyword=音乐&page=1&pageSize=20
```

- `keyword`：默认空字符串，最多200字符。
- `page`：默认1，最小1。
- `pageSize`：默认20，范围1至100。
- 成功响应为 `{code:0,message:"操作成功",data:{...}}`，参数错误返回 HTTP400，服务异常返回 HTTP500。

兼容入口仅接受 JSON 请求并使用旧 `{success,message}` 响应；请求中的凭证均忽略。该入口不涵盖其他旧 Servlet 行为。

新服务不提供旧站静态页面或跨域放行，接入时转发已实现的 `/api/v1/` 路径。配套应用见[Vue 前端](../frontend/README.md)。

## 会话与音乐库接口

这些接口接收 POST JSON，暂沿用 Vue 已有的 `{success,...}` 字段；认证失败为 HTTP401，非法参数为400，歌曲不存在或未发布为404，内部异常为500。ID 均为字符串。本人身份只取自 Authorization Bearer，客户端 userId 不参与身份判断。

| 路径 | 请求字段 | 成功字段 |
| --- | --- | --- |
| `/api/v1/session/login` | email, password | user, tokens |
| `/api/v1/session/refresh` | refreshToken | tokens |
| `/api/v1/session/logout` | refreshToken；可附 access Bearer | success |
| `/api/v1/session/profile` | 无 | user |
| `/api/v1/library/song` | songId；匿名可读 | song |
| `/api/v1/library/favorites` | page, size，默认1/20，上限100 | favorites.list, favorites.total |
| `/api/v1/library/states` | songIds，逗号分隔，最多100个 | states |
| `/api/v1/library/favorite` | songId | success |
| `/api/v1/library/unfavorite` | songId | success |

新增表结构见 [003_auth_sessions.sql](src/main/resources/db/003_auth_sessions.sql)，由开发者显式执行，应用不自动建表。Boot 与过渡期 WAR 必须使用同一份会话表，并更新到支持 Boot 令牌校验的 WAR。没有该表时新登录不可用；未更新的 WAR 不接受 Boot 令牌。

Boot 使用安全随机的不透明双令牌，只存 SHA-256 摘要。access 有效30分钟，refresh 固定7天；刷新签发新的 access，保留 refresh，退出通过任一凭证撤销整个会话及其所有 access。数据库校验失败时拒绝访问。旧 WAR 对 Boot access 执行共享表校验，覆盖业务、HLS清单、密钥和切片；旧刷新入口拒绝 Boot 凭证，旧退出入口也持久撤销 Boot 会话。旧 JWT 仍仅在旧 WAR 内签发、验证和进程内撤销，Boot不接受旧JWT。切换版本后已有标签页需重新登录。

共享会话桥接仅为仍运行的旧站保留，Vue 不再依赖它。会话表过期记录清理、多实例容量、真实部署与跨进程重启验收尚未完成。


## 账号、评论与文件业务

以下接口接收 POST JSON，成功沿用 `{success:true,...}`；本人身份只取自 Bearer 会话，忽略客户端 userId/singerId。

| 路径 | 功能 |
| --- | --- |
| `/api/v1/account/verification-code` | 发送邮箱验证码 |
| `/api/v1/account/verification` | 独立校验并消费验证码 |
| `/api/v1/account/registration` | 注册：username、email、password、verificationCode |
| `/api/v1/account/password-reset` | 重置：email、newPassword、verificationCode |
| `/api/v1/comments/page`、`/count` | 歌曲评论与回复分页、评论总数 |
| `/api/v1/comments`、`/comments/like` | 发布/回复、幂等点赞及取消 |
| `/api/v1/creator/profile` | 本人资料与头像更新 |
| `/api/v1/creator/status`、`/application` | 本人音乐人状态与申请 |
| `/api/v1/creator/upload` | 审核通过的音乐人上传，歌曲初始为待审核 |
| `/api/v1/recognition` | 歌词推断后匹配已发布曲库 |

首次启用需在现有业务表上显式执行 [004_email_verifications.sql](src/main/resources/db/004_email_verifications.sql) 和 [005_musician_materials.sql](src/main/resources/db/005_musician_materials.sql)。收藏/评论联合唯一索引沿用旧库已有约束；这些增量脚本不初始化或覆盖业务数据。

验证码摘要持久化，10分钟有效、60秒冷却、最多5次校验且成功后一次性消费。注册/重置直接提交验证码，不要预先调用独立校验接口。账号写入失败会回滚验证码消费，错误尝试仍会计数。当前沿用旧 users.email 的25字符限制；新密码至少8字符且UTF-8不超过72字节。重置成功撤销该账号全部 Boot 会话；旧站历史 JWT 不纳入此持久撤销域。

评论暂支持歌曲资源 resourceType=1，分页和写入均检查歌曲已发布、评论有效及回复关系。点赞记录、计数和回复记录处于同一事务。

## 外部服务与存储

配置见 [`.env.example`](.env.example)，真实凭证仅填本地环境文件或系统环境变量。

- 邮件使用 `SMTP_HOST/PORT/USER/PASSWORD/FROM`，默认465隐式TLS；587需启用STARTTLS并关闭隐式SSL。连接、读写均设超时，未配置或发送失败不会返回成功。
- 识曲使用 `RECOGNITION_ENDPOINT/API_KEY/MODEL`，沿用星火HTTP推断方式，凭证不含Bearer前缀。识曲并非音频指纹识别，未配置返回503。
- `STORAGE_ROOT`：新音频、公开图片/歌词及私有申请材料的独立目录。客户端文件名不参与路径生成；失败只清理本次创建的文件，不覆盖现有文件。
- `LEGACY_STORAGE_ROOT`：已有图片和歌词的根目录，应包含 `static/`，仅用于只读访问。
- `LEGACY_AUDIO_ROOT`：旧音频目录；未配置时沿用 `LEGACY_STORAGE_ROOT`。现有歌曲 audio_url 应指向该目录中的有效文件；不会自动复制或改写旧库路径。
- `LEGACY_MIRROR_ROOT`：可选的已校验旧资源副本，内部为 `assets/static/` 和 `audio/`；优先读取副本，缺失时回退旧目录。保留旧根配置作为音频相对路径映射依据，无需改写数据库中的历史路径。副本应在切换前逐文件校验，原资源不自动删除。
- `HLS_ROOT_DIR`：全新的转换输出目录，禁止配置为现有旧站HLS目录；`FFMPEG_PATH` 指定可执行文件。

`GET /api/v1/assets/...` 仅提供白名单图片和歌词，身份证材料及原始音频不公开。HLS通过 `/api/v1/media/hls?action=processMp3|checkStatus|serveHls` 访问，清单、密钥、切片均验证会话及歌曲审核状态。切片支持Range；原生HLS的查询令牌会传播至子请求，部署时使用HTTPS且避免记录查询凭证。转换保留有界队列、同歌去重、完整VOD和原子发布；新上传在数据库事务提交后自动进入有界转码队列，回滚不会转码；待审核歌曲即使已转码也不能公开读取。排队或转换失败不改变已成功提交的上传结果，审核发布后播放可重试；既有歌曲仍可在首次播放时按需转换。当前为单实例协调，不自动回收历史转换文件。

`mvn -f backend/pom.xml verify` 执行隔离数据库与HTTP回归；已安装FFmpeg时使用 `-Pmedia-regression` 额外验证真实转换、长音频、解码、失败重试和鉴权。隔离邮件/识曲替身不代表真实投递或外部服务验收，真实MySQL、Safari和部署拓扑仍需联调。

旧站独立 WebSocket 广播示例同时提供 `/websocket` 与 `/api/v1/websocket`，保持连接提示与文本广播行为，默认同源访问；它不提供音乐业务通知或聊天历史。开发代理已开启 WebSocket，生产代理需支持 Upgrade。
