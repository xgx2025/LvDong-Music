# LvDong-Music · 律动音乐

律动音乐是一个 JavaWeb 音乐网站，围绕歌曲搜索、HLS 在线播放、用户账号、收藏、评论和歌曲上传构建。目前保留 Servlet 版本，同时逐步迁移至 Vue 3 与 Spring Boot 分层单体。

## 项目组成

| 模块 | 技术栈 | 当前能力 |
| --- | --- | --- |
| 旧站 `src/` | Java 16 编译目标、Servlet 3.1、JDBC、HikariCP、MySQL、Tomcat 9 | 用户体系、搜索、HLS 播放、收藏、评论、上传及音乐人申请等业务 |
| [新前端](frontend/README.md) | Vue 3、TypeScript、Vite、Vue Router、Pinia、HLS.js | 公开搜索、账号与个人中心、收藏、评论、上传、识曲、歌词与常驻播放器 |
| [独立后端](backend/README.md) | Java 21、Spring Boot 4.1.2、JDBC | 匿名公开搜索 API 与旧 JSON 搜索兼容入口 |

新前端已接入主要旧站业务，其中账号、收藏、评论、上传、识曲和媒体继续调用 Servlet 服务；独立后端目前只提供公开搜索。推荐、歌单与 AI 演示不代表完整后端能力；反馈目前支持本机导出，第三方 OAuth 尚未接入。

历史学习版本见 [v1.0.0](https://github.com/xgx2025/LvDong-Music/tree/v1.0.0)。

## 主要设计

- **业务分层**：旧站采用 Servlet → Service → DAO，新后端采用 Controller → Service → JDBC DAO，事务由 Service 管理。
- **搜索**：只返回审核通过的歌曲，按歌曲分页并批量获取关联歌手；新 API 使用字符串 ID，避免前端大整数精度丢失。
- **媒体播放**：FFmpeg 生成完整加密 HLS，转换任务有界排队、同歌去重，清单、切片和密钥经过鉴权。
- **收藏一致性**：收藏与取消收藏支持幂等，记录变更和计数更新处于同一事务。
- **常驻播放器**：新前端由应用级状态管理单一 Audio 引擎，路由切换保留队列及播放进度。

## 目录结构

```text
ListenToMusic
├── src/main/java/          Servlet、Service、DAO 与工具类
├── src/main/resources/     配置模板、日志配置与 SQL
├── src/main/webapp/         旧站页面与静态资源
├── src/test/               Java 回归测试源码
├── frontend/               Vue 应用及前端测试源码
├── backend/                独立 Spring Boot 项目
└── pom.xml                 旧站 WAR 构建入口
```

## 基本运行

三个模块分别构建，根 Maven 项目不聚合 `backend/`。

### Servlet 旧站

环境：JDK 16+、Maven、MySQL 8、Tomcat 9；HLS 转换还需要 FFmpeg。

配置项见 [application.properties.example](src/main/resources/application.properties.example)。通过环境变量或 `APP_CONFIG_FILE` 指定外部配置；真实配置文件默认不打入 WAR。

```sh
mvn clean verify
```

产物为 `target/ListenToMusic.war`。旧页面依赖 `/xgxmusic` context，运行时需使用该部署路径。旧站采用 `javax.servlet`，不能直接放入 Tomcat 10/11。

### Vue 前端

```sh
cd frontend
npm ci
npm run dev
```

### Spring Boot 后端

使用 Java 21，设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 后启动：

```sh
mvn -f backend/pom.xml clean verify
java -jar backend/target/lvdong-backend-0.1.0-SNAPSHOT.jar
```

新后端默认端口为8081。前端默认代理公开 API 到此端口，媒体仍需单独连接旧站。

## 使用说明

项目用于学习与技术实践。音乐、图片等资源的版权归各自权利人所有，使用或再分发时需自行确认授权。数据库密码、JWT 密钥及第三方服务凭证应通过本地配置或环境变量提供。
