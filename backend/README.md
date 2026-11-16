# 律动音乐 · 独立后端

基于 Java 21 / Spring Boot 4.1.2 的分层单体，目前提供公开搜索、持久会话、本人收藏和歌曲详情。旧 Servlet WAR 在仓库根目录保留独立构建入口，新服务尚未替换整个旧站。

## 当前能力

- `GET /api/v1/search`：公开搜索审核通过的歌曲及相关歌手。
- `POST /searchServlet?action=search`：兼容旧 JSON 搜索请求。
- Controller → Service → JDBC DAO 分层，Service 管理查询快照与写入事务。
- 参数校验、统一异常响应、字符串 ID 及当前页歌手批量查询。

登录、刷新、退出和本人资料使用持久会话；媒体、账号其他写入、上传和评论仍由旧 WAR 提供。搜索统一按匿名处理，`userHasFavorite=false`；专辑和歌单结果暂为空。

## 构建与运行

需要 Java 21、Maven，以及具有现有业务表的 MySQL 数据库。

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

此桥接仅用于尚未迁移的 Servlet 业务；当这些业务和媒体全部迁入 Boot 后移除。媒体转换队列与文件存储仍属于 WAR。会话表过期记录清理、多实例容量、真实联合部署与跨进程重启验收尚未完成。
