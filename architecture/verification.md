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

## 首阶段未验证项

没有运行真实 MySQL、Tomcat、浏览器、FFmpeg、外部 SMTP 或识曲服务；没有完成播放器端到端，也没有完成 Vue/Boot 新入口。H2 不覆盖 MySQL 字符集、执行计划、锁竞争、现网表差异。HLS 可靠性及黑名单持久化的限制见 [迁移方案](migration.md)。

下一步首先实施收藏约束与媒体任务可靠性：先只读检查现存重复收藏和媒体路径，形成增量迁移，再以 MySQL 并发回归与 FFmpeg 短/长音频回归验收。

## 仓库清理

按引用检查删除过时的 CLAUDE.md、没有构建入口的旧 npm 清单，以及 temp/、后台测试页、WEB-INF 旧页面副本、b.html 反馈副本、支付测试页、DASH 实验页和默认 Hello World JSP。旧 npm 依赖目录与临时验证日志也已清理。同步移除失效的公开页面白名单项，修正 README 目录结构。

业务入口、媒体/歌词/图片、数据库脚本、配置模板、回归测试与迁移资料保留；历史源码仍可通过 v1.0.0 获取。清理后重新执行构建与10项回归，并检查 WAR 不再打包上述原型。


## 第二阶段：收藏事务与媒体任务（2026-11-16）

本轮新增8项 H2/Servlet/FFmpeg 回归及1项真实 MySQL 回归，连同原10项，共19项全部通过；WAR检查仍不包含私人配置、H2或测试类。

- `mvn -o -Pmedia-regression clean verify`：18项通过，包含实际FFmpeg生成的1秒短音频、55秒且超过50片的长音频、AES加密解码、同歌Future去重、失败传播和重试、旧文件保留、原生HLS清单子请求URL与16字节密钥响应。
- `python scripts/run-isolated-mysql.py`：另1项 MySQL 8.0.41 回归通过；以全新临时数据目录和随机本地端口启动实例，24次并发收藏/取消均保持一条或零条记录及一致计数，计数写入失败时插入回滚。随后在隔离库连续执行两次增量索引脚本并验证唯一索引存在。没有连接现有数据库。
- 默认 `mvn clean verify` 排除需要外部二进制的 IntegrationTest；媒体测试启用上面的 profile，FFmpeg须在PATH，或通过 `-Dffmpeg.test.path=…` 指定。MySQL脚本需 mysql/mysqld/mysqladmin 在同一目录，自动从PATH发现，也可用环境变量 MYSQL_TEST_BIN 指定。脚本只创建新库，不DROP/TRUNCATE现存表；临时实例通过mysqladmin正常关闭，测试数据保留在target供检查。

验证中发现Windows的mysqld可能由父进程监管子进程，直接terminate父进程不能释放全部文件；已修正为正常关闭实例并等待监管进程退出。最终重新执行 clean verify 与隔离脚本均成功。生产迁移脚本尚未执行；需暂停收藏写入、先审查重复记录与计数偏差，再执行002。旧001脚本会删除重复评论点赞，不应因本次升级自动执行。

尚未完成真实浏览器端到端。新的HLS任务仅在单实例内协调；旧版本切片不自动回收；播放首次触发转换并等待完整VOD，因此大文件首次等待可能超过旧播放器30秒轮询上限。上传独立存储、上传完成预处理、Boot运行框架和Vue应用外壳仍是后续工作。

VOD参数参考 [FFmpeg官方HLS文档](https://ffmpeg.org/ffmpeg-formats.html#hls-2)；具体行为以本轮实际转换/解码回归为依据。
