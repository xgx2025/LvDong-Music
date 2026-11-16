# 律动音乐渐进迁移方案

## 历史与交付边界

`v1.0.0` 保留在 `a740ca268840568dedfd85d96223c8dd29378d73`，发布为 Servlet / JDBC JavaWeb 学习版本。升级工作在 `xgx-dev`，未授权时不推送、不发布 Release。当前运行实现仍为 Servlet 3.1 / JDBC / HikariCP / MySQL / WAR / Tomcat 9。

## 目标结构

- `frontend/`：Vue 3、TypeScript、Vite、Router、Pinia；应用外壳拥有常驻播放器，路由只替换页面区域。Audio/HLS 引擎独立于组件；队列、播放位置和用户状态集中管理，退出时清理用户信息但不意外重建播放引擎。
- `backend/`：Spring Boot 分层单体，包按业务域组织 controller/service/dao/domain/config。事务归 Service，数据访问留 DAO；先迁移 JDBC，按业务复杂度决定是否采用 MyBatis，不预设 MyBatis-Plus。
- 媒体目录独立于源码和容器部署目录；通过配置访问旧文件，增量复制并核对校验和后才切换路径。转换任务独立管理，以歌曲及源文件版本去重，避免播放请求删除正在使用的目录。
- 首先采用 CSS 动效；复杂手势再引入 Motion for Vue，时间轴才考虑 GSAP，明确 3D 需求才考虑 Three.js。封面取色背景、歌词平滑跟随、迷你播放器展开为重点；移动端提供减少动态效果设置并尊重 `prefers-reduced-motion`。

## Java / Spring 版本决策

本机已实测 Java 21.0.9、Maven 3.9.10。首阶段保留 Java 16 编译目标，避免改变历史 Tomcat 部署要求。后端迁移目标为 Java 21 和 Spring Boot 4.1.2（本次查询官方项目页显示的稳定版）；实际新增 backend/pom.xml 时重新核对受支持分支及安全补丁，再锁定 BOM。

依据：[官方项目页](https://spring.io/projects/spring-boot/)、[系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[4.0 迁移说明](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)。本次官方文档说明 Java 17+、Maven 3.6.3+、Tomcat 11 / Servlet 6.1。Java 21 满足要求，Tomcat 9 不满足新容器要求。Servlet/Filter/WebSocket 的 `javax.*` 应改为相应 `jakarta.*`；JDBC `java.sql`、加密 `javax.crypto` 不作机械替换。不要直接把旧 WAR 放入新容器。

旧 jjwt 0.9.1、javax JAXB、邮件库、WebSocket、日志和 JSON 依赖需逐项替换/验证；不能把旧依赖原样带入 Boot BOM。验证门槛是编译、登录/刷新/撤销、异常响应、SQL事务与媒体回归全部通过，再切换默认启动方式。

## 分阶段交付与验收

| 阶段 | 实现范围 | 验收门槛 |
| --- | --- | --- |
| 1：稳定基线（本轮） | 匿名搜索、歌曲审核过滤、歌手级分页、上传审核资格、关联事务归属、JSON 退出与刷新撤销、排除私人配置、接口契约 | 隔离 H2 SQL 回归与 Servlet 请求绑定回归；`mvn clean verify`；WAR 无 application.properties 与 H2 测试依赖 |
| 2：收藏与媒体可靠性 | 联合唯一约束增量迁移，重复数据只报告不自动删除；收藏幂等及计数事务；HLS 状态机、限流任务池、去重、错误传播、完整 VOD 列表、独立存储 | MySQL 并发收藏无重复且计数一致；FFmpeg 短音频、超过50切片长音频、同歌并发、失败重试、撤审缓存拒绝；旧文件保持可读 |
| 3：后端运行框架 | Java 21 / Boot；统一响应、校验、异常、认证上下文、事务；外部配置；旧端点兼容适配 | 集成数据库与嵌入式容器启动，旧契约测试通过；JDBC DAO 不再管理服务事务；会话撤销跨重启持久化 |
| 4：前端公共外壳 | Vue/TS/Vite/Router/Pinia；常驻播放器、队列、请求层、真实搜索/评论/个人中心；减少动效 | 路由切换不换 Audio 实例、不重新加载音源；手机触控/键盘可操作；退出状态一致；类型检查与生产构建通过 |
| 5：功能与体验完善 | 接真实推荐、歌词跟随、封面背景、展开手势、去除/标识演示功能，清理旧页面 | 浏览器端到端：搜索→播放→评论→个人中心不断播；网络异常恢复；移动端性能和减少动态效果验证 |

## 复核清单与剩余风险

已确认并修复：匿名 userId 解析、公开歌曲搜索/统计/源路径/识曲精确查询审核过滤、多歌手 JOIN 后分页缺页、歌单数量误用歌手计数、上传入口音乐人审核资格、关联 DAO 提前提交及独立调用连接泄漏、JSON 登出刷新凭证未绑定、刷新未检查黑名单。

当前歌单查询和计数仍为空实现，返回空列表/0，不能宣称歌单功能已完成。搜索歌手和评论仍有 N+1；收藏初始状态、上传字段长度与数据库字段不一致、时长秒数迁移仍待处理。前端多页重复代码、CDN Tailwind、散落 localStorage、HTTP/context path 硬编码及静态/AI演示仍待阶段4/5。

HLS 仍存在播放触发转换、无去重、清理目录竞态、异常恢复成成功、以两个分片判断就绪、50片窗口的问题；另复核发现生成的 encryption.key URL 与 serveHls 文件白名单不一致，阶段2必须覆盖加密播放实际测试。当前缓存访问增加审核检查，但不宣称 HLS 播放链路已经可用。

当前刷新凭证固定期限复用，退出将其加入进程内黑名单；避免轮换后旧令牌链绕过退出。黑名单仍非持久化，重启会丢失；已签发的其他 access token 仍可活到自身过期，下一阶段需持久化会话标识及统一撤销。尚未完成真实 MySQL/Tomcat/浏览器端到端，H2 仅用于隔离 SQL/事务回归。

不运行历史数据库全量初始化脚本，不清理现有媒体，不提交 docs/、本地配置或构建产物。
