# 首阶段验证记录

执行环境：2026-11-16，Java 21.0.9 / Maven 3.9.10，分支 `xgx-dev`。现有 Servlet/WAR 仍按 Java 16 目标编译。

## 自动化验证

执行 `mvn -o clean verify` 通过。首次离线运行缺少 Surefire JUnit3 provider，经联网构建补齐后可离线复验。当前保留原 JUnit 测试框架，新增 H2 仅 test scope。

| 回归类 | 用例数 | 覆盖 |
| --- | --- | --- |
| MusicRegressionTest | 4 | 匿名歌曲查询、审核状态与源路径/识曲精确查询过滤、多歌手分页与计数、上层事务回滚、独立批量写入失败回滚 |
| SessionRegressionTest | 3 | 实际 BaseServlet JSON logout 参数绑定、刷新令牌复用和撤销后拒绝、缺失令牌拒绝、无效 access 不阻断 refresh 撤销 |
| UploadRegressionTest | 1 | 申请中/认证/拒绝三种状态，仅认证用户可进入上传服务 |
| SearchRegressionTest | 1 | 非法 token 按匿名搜索并钳制页码 |
| HlsPublicationRegressionTest | 1 | 撤审歌曲在读取缓存文件前返回404 |

全部10项通过，0失败、0错误。SQL 回归使用 `jdbc:h2:mem:lvdong_regression;MODE=MySQL` 和人工最小表结构，不导入历史数据；检测到外部数据库配置覆盖时测试拒绝运行。上传测试替换业务服务，不写媒体；Servlet 请求/响应使用代理对象，验证绑定和边界，不能代替容器端到端测试。

以 ZIP 目录检查 `target/ListenToMusic.war`：无 `*/application.properties`、H2 jar 或 RegressionTest 类。真实配置未读取输出或提交。WAR 默认需要环境变量或 `APP_CONFIG_FILE`；仅复制本地配置到源码目录不再让打包后的 WAR 自动获取配置。

`git diff --check` 通过；`v1.0.0` 标签仍指向原冻结提交。本轮不执行数据库升级、不删除媒体、不推送、不发布。

## 未验证项

没有运行真实 MySQL、Tomcat、浏览器、FFmpeg、外部 SMTP 或识曲服务；没有完成播放器端到端，也没有完成 Vue/Boot 新入口。H2 不覆盖 MySQL 字符集、执行计划、锁竞争、现网表差异。HLS 可靠性及黑名单持久化的限制见 [迁移方案](migration.md)。

下一步首先实施收藏约束与媒体任务可靠性：先只读检查现存重复收藏和媒体路径，形成增量迁移，再以 MySQL 并发回归与 FFmpeg 短/长音频回归验收。
