# 律动音乐独立后端

Java 21 / Spring Boot 4.1.2 分层单体的首个迁移切片。当前提供匿名公开搜索，旧 Servlet WAR 保留在仓库根目录独立构建。新后端没有登录、上传、播放、评论等入口，不能替换整个旧服务。

## 构建与启动

```sh
mvn -f backend/pom.xml clean verify
java -jar backend/target/lvdong-backend-0.1.0-SNAPSHOT.jar
```

运行前配置环境变量 `DB_URL`（MySQL JDBC URL）、`DB_USERNAME`、`DB_PASSWORD`；可选 `PORT`，默认 8081。也可按 Spring 标准方式使用 `SPRING_CONFIG_ADDITIONAL_LOCATION` 指向外部配置目录。不要把凭证提交到仓库。旧服务的 `APP_CONFIG_FILE` 不由新后端加载。

后端使用现有 `songs`、`song_singer`、`singers` 表，只执行查询，不自动建表或执行升级脚本。连接池最多8连接，查询计数和分页在 Service 的只读、REPEATABLE_READ 事务中执行；歌曲歌手信息按当前页批量查询。需使用支持事务的表（MySQL InnoDB）。

示例：`GET http://localhost:8081/api/v1/search?keyword=音乐&page=1&pageSize=20`。成功响应为 `{code:0,message:"操作成功",data:{...}}`。公开 ID 均为字符串。

兼容入口为 `POST /searchServlet?action=search`，仅支持 JSON `keyword/page/pageSize`，返回旧的 `{success,message}` 结构。Bearer 和请求体中的旧凭证字段均不解析，统一按匿名查询；`userHasFavorite=false`。旧 WAR 的原搜索入口及认证行为不变。部署时仅把明确迁移的搜索请求路由到新服务，其他路径仍由旧服务处理。新服务不提供静态页面、跨域放行或旧服务的全部 Servlet 行为。

新 API 的 page>=1、1<=pageSize<=100、keyword<=200；越界返回 HTTP400。兼容入口沿用旧页码钳制，但也限制关键词长度。不提供专辑/歌单实际查询，保留数组为空、计数为0。完整约定见 [OpenAPI](openapi.yaml) 与 [迁移契约](../architecture/api-contract.md)。

## 验证

默认构建使用显式固定的内存 H2、随机 HTTP 端口和真实嵌入式 Tomcat，不读取旧项目配置或连接开发数据库。

```sh
python scripts/run-isolated-mysql.py --backend
```

此命令需先完成后端构建，并要求 `mysql`、`mysqld`、`mysqladmin` 在 PATH，或设置 `MYSQL_TEST_BIN`。脚本新建临时数据目录及本地随机端口，运行同一组 HTTP 回归并启动打包 JAR 验证查询，随后关闭临时 Java/MySQL 进程。测试类只能由该脚本提供的隔离标记及临时库 URL 启用。临时数据保留在被忽略的 `target/`，不连接现有数据库。

认证与跨重启会话撤销、收藏写事务、媒体存储和 Vue 外壳仍待后续切片迁移，见 [阶段方案](../architecture/migration.md)。
