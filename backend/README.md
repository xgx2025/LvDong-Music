# 律动音乐 · 独立后端

基于 Java 21 / Spring Boot 4.1.2 的分层单体，目前提供匿名公开搜索。旧 Servlet WAR 在仓库根目录保留独立构建入口，新服务尚未替换整个旧站。

## 当前能力

- `GET /api/v1/search`：公开搜索审核通过的歌曲及相关歌手。
- `POST /searchServlet?action=search`：兼容旧 JSON 搜索请求。
- Controller → Service → JDBC DAO 分层，Service 管理只读事务。
- 参数校验、统一异常响应、字符串 ID 及当前页歌手批量查询。

当前不提供登录、播放、上传、评论或收藏写入。搜索统一按匿名处理，`userHasFavorite=false`；专辑和歌单结果暂为空。

## 构建与运行

需要 Java 21、Maven，以及具有现有业务表的 MySQL 数据库。

```sh
mvn -f backend/pom.xml clean verify
java -jar backend/target/lvdong-backend-0.1.0-SNAPSHOT.jar
```

通过环境变量设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`；`PORT` 可选，默认8081。也可使用 Spring 标准外部配置。新服务不加载旧站的 `APP_CONFIG_FILE`，不自动建表或执行升级脚本。

## 搜索接口

```text
GET /api/v1/search?keyword=音乐&page=1&pageSize=20
```

- `keyword`：默认空字符串，最多200字符。
- `page`：默认1，最小1。
- `pageSize`：默认20，范围1至100。
- 成功响应为 `{code:0,message:"操作成功",data:{...}}`，参数错误返回 HTTP400，服务异常返回 HTTP500。

兼容入口仅接受 JSON 请求并使用旧 `{success,message}` 响应；请求中的凭证均忽略。该入口不涵盖其他旧 Servlet 行为。

新服务不提供旧站静态页面或跨域放行，接入时仅转发已实现的搜索路径。配套应用见[Vue 前端](../frontend/README.md)。
