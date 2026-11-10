# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

音乐流媒体 Web 应用（"律动音乐" / ListenToMusic），基于传统 Java Servlet WAR 构建 —— **不是** Spring Boot。部署到 Tomcat，使用 MySQL，原生 JDBC（无 ORM 框架）。

## 构建与运行

```bash
mvn clean package          # 生成 target/ListenToMusic.war
```

- 将 WAR 包部署到 Tomcat 容器
- 需要 MySQL 运行在 `127.0.0.1:3306`，数据库名 `lvdong_music_db`（凭证见 `DatabaseUtil.java`）
- HLS 流媒体需要安装 FFmpeg（路径配置在 `WEB-INF/web.xml`）
- 项目无测试套件（不存在 `src/test` 目录）

## 项目架构

三层架构 + 自定义 Action 分发机制：

```
Servlet (servlet/) → Service (service/) → DAO (dao/) → MySQL (JDBC)
```

**Servlet 路由：** 所有 Servlet 继承 `BaseServlet`，通过反射根据 `?action=<methodName>` 路由请求。请求参数通过自定义注解 `@ParamName` 绑定到方法参数。JSON 响应使用 Gson 序列化。

**数据访问：** 原生 JDBC + HikariCP 连接池。DAO 接口实现在 `dao/impl/` 目录下。`DatabaseUtil` 通过 `ThreadLocal` 管理连接以支持事务。ID 使用雪花算法生成（`SnowFlakeUtil`）。

**认证：** JWT 双 token 方案 —— `accessToken`（30分钟）+ `refreshToken`（7天）。`TokenFilter` 对受保护端点校验 Bearer token。公开路径和特定 action 在过滤器中白名单放行。

**过滤器：** `CorsFilter`（CORS 头）、`TokenFilter`（JWT 校验）。配置在 `web.xml` 中。

## 前端

服务端渲染的 HTML 页面（无 SPA 构建流程）。通过 CDN 引入 Tailwind CSS、Axios、Vue.js、Font Awesome、HLS.js。静态资源位于 `src/main/webapp/static/`。

- `config.js` — 服务器地址配置
- `axios-config.js` — Axios 拦截器（token 注入、刷新逻辑）
- `auth.js` — 前端 JWT 认证工具
- `player.js` — 音乐播放器逻辑

部分页面已加密（`webapp/encrypted/` 下的 `.enc.html`），通过 `decryptor.js` + CryptoJS 在客户端解密。

## 关键配置位置

| 配置项 | 位置 |
|--------|------|
| 数据库凭证 | `src/main/java/utils/DatabaseUtil.java` |
| Servlet/过滤器映射 | `src/main/webapp/WEB-INF/web.xml` |
| 前端服务器地址 | `src/main/webapp/static/js/config.js` |
| FFmpeg 路径 | `web.xml`（HLS servlet 的 init-param） |

## API 端点（web.xml 中的 servlet 映射）

`/userServlet`、`/authServlet`、`/musicServlet`、`/singerServlet`、`/searchServlet`、`/commentServlet`、`/favoriteServlet`、`/recognitionServlet`、`/keyServlet`、`/hls`

所有端点均使用 `?action=methodName` 分发模式。
