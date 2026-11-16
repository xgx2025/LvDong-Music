# 首阶段接口兼容契约

此文档冻结已有 Servlet 的必要行为；计划中的 `/api/v1` 尚未实现。路径均相对部署 context，不把 `/xgxmusic` 当成业务契约。ID 为十进制字符串，避免 JavaScript 大整数精度丢失。

| 请求 | 入参 / 身份 | 首阶段响应及保证 |
| --- | --- | --- |
| POST `searchServlet?action=search` | JSON keyword、page、pageSize；Bearer 可选 | `{success:true,message:{songCount,albumCount,singerCount,playlistCount,songs,albums,singers,playlists}}`；缺失/无效/撤销 token 降级匿名；歌曲仅 state=1；按歌曲而非歌手关联行分页，同相关度以歌曲ID稳定排序 |
| POST `musicServlet?action=upload` | Bearer 必须；songName、singerIdStr（分号分隔）、duration（mm:ss）、album、musicFile、lyricFile、coverImage | `{success:true}` 或 `{success:false,message}`；服务端检查当前用户 singers.status=1 且在关联歌手中；未通过资格不调用上传服务、不写媒体 |
| POST `userServlet?action=refreshToken` | JSON refreshToken | `{tokens:{accessToken,refreshToken}}`；refreshToken 固定到期时间并复用；缺失、无效、过期、撤销令牌不得换取 access token |
| POST `userServlet?action=logout` | Bearer accessToken；JSON 或表单 refreshToken | `{success:true,message}`；未过期且有效的两个凭证分别加入黑名单；前端无论请求结果都清理本地身份 |
| GET `hls?action=serveHls&songId=…&fileName=…` | Bearer 或 token 查询参数 | 原媒体响应；每次请求检查歌曲仍审核通过，缓存存在也不能绕过；密钥和切片通过同一鉴权入口；清单仅在完整转换成功后发布 |

审核状态沿用现有数据库定义：songs.state 为 0待审/1通过/2不通过；singers.status 为 0申请/1认证。数据库保持不变，Song.duration 暂仍为字符串。歌单未实现，playlists=[]、playlistCount=0。登录/刷新响应维持历史字段形状，不在旧客户端仍使用时强行改成统一 envelope。

后续新 API 采用统一 `{code,message,data}`、HTTP 400/401/403/404/409/500 与结构化校验错误，通过兼容适配保留旧端点。播放器任务状态拟为 QUEUED/RUNNING/READY/FAILED，失败不得伪装 READY。新增接口以 OpenAPI 和集成测试锁定后才由 Vue 使用。

部署配置：WAR 默认排除 `src/main/resources/application.properties`；使用环境变量，或 `APP_CONFIG_FILE` 指向外部 properties 文件。加载优先级：逐项环境变量 > 外部文件 > classpath 配置。外部文件显式指定却不可读时启动失败，不静默回退。配置样例不会包含真实值。


## 第二阶段增量契约

- 收藏与取消收藏重复请求均成功，计数仅随实际记录变更。歌曲不存在仍报错；Service 事务锁定歌曲行，DAO不提交事务或吞掉SQL异常。生产数据库需执行 `002_song_likes_unique.sql`；脚本检测到重复记录时停止，不自动删除数据。
- `hls?action=processMp3` 返回原有 success/hlsUrl；相同输出目录的正在执行任务复用 Future。最多2个运行任务和16个排队任务，过载状态为 FAILED。
- `hls?action=checkStatus` 保留 ready，并增加 state：NOT_REQUESTED / QUEUED / RUNNING / READY / FAILED。只有完整清单和全部切片存在时才为 READY；FAILED 提供可显示的通用 message。旧播放器轮询遇到 FAILED 立即显示错误。
- VOD 使用完整列表，不再按50片滚动。每次转换使用唯一的切片和AES密钥文件名，成功后原子替换 index.m3u8；失败不覆盖旧清单、不删除现存媒体。源路径、大小和修改时间用于识别缓存版本；旧文件可读，下一次任务会按版本重新生成。
- 清单返回时把切片/密钥URL改写为鉴权Servlet；原生HLS查询token传给子请求。清单和密钥 no-store，切片 private/no-store。只有16字节密钥允许返回，内部keyinfo和转换日志不在文件白名单内。实际部署应使用HTTPS，访问日志避免记录token查询参数。
