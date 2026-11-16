# 首阶段接口兼容契约

此文档冻结已有 Servlet 的必要行为；计划中的 `/api/v1` 尚未实现。路径均相对部署 context，不把 `/xgxmusic` 当成业务契约。ID 为十进制字符串，避免 JavaScript 大整数精度丢失。

| 请求 | 入参 / 身份 | 首阶段响应及保证 |
| --- | --- | --- |
| POST `searchServlet?action=search` | JSON keyword、page、pageSize；Bearer 可选 | `{success:true,message:{songCount,albumCount,singerCount,playlistCount,songs,albums,singers,playlists}}`；缺失/无效/撤销 token 降级匿名；歌曲仅 state=1；按歌曲而非歌手关联行分页，同相关度以歌曲ID稳定排序 |
| POST `musicServlet?action=upload` | Bearer 必须；songName、singerIdStr（分号分隔）、duration（mm:ss）、album、musicFile、lyricFile、coverImage | `{success:true}` 或 `{success:false,message}`；服务端检查当前用户 singers.status=1 且在关联歌手中；未通过资格不调用上传服务、不写媒体 |
| POST `userServlet?action=refreshToken` | JSON refreshToken | `{tokens:{accessToken,refreshToken}}`；refreshToken 固定到期时间并复用；缺失、无效、过期、撤销令牌不得换取 access token |
| POST `userServlet?action=logout` | Bearer accessToken；JSON 或表单 refreshToken | `{success:true,message}`；未过期且有效的两个凭证分别加入黑名单；前端无论请求结果都清理本地身份 |
| GET `hls?action=serveHls&songId=…&fileName=…` | Bearer 或 token 查询参数 | 原媒体响应；每次请求检查歌曲仍审核通过，缓存存在也不能绕过；HLS 完整可靠性尚待阶段2 |

审核状态沿用现有数据库定义：songs.state 为 0待审/1通过/2不通过；singers.status 为 0申请/1认证。数据库保持不变，Song.duration 暂仍为字符串。歌单未实现，playlists=[]、playlistCount=0。登录/刷新响应维持历史字段形状，不在旧客户端仍使用时强行改成统一 envelope。

后续新 API 采用统一 `{code,message,data}`、HTTP 400/401/403/404/409/500 与结构化校验错误，通过兼容适配保留旧端点。播放器任务状态拟为 QUEUED/RUNNING/READY/FAILED，失败不得伪装 READY。新增接口以 OpenAPI 和集成测试锁定后才由 Vue 使用。

部署配置：WAR 默认排除 `src/main/resources/application.properties`；使用环境变量，或 `APP_CONFIG_FILE` 指向外部 properties 文件。加载优先级：逐项环境变量 > 外部文件 > classpath 配置。外部文件显式指定却不可读时启动失败，不静默回退。配置样例不会包含真实值。
