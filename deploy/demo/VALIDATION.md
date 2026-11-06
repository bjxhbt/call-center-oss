# 验收记录 · 2026-10-28

## RustFS 部署与验收

- 已部署独立的 RustFS 1.0.4 容器与专用数据卷，使用 `xhbt-demo-recordings` 存储桶；其他项目的 RustFS 未复用。
- 后端使用 Docker 内网访问 RustFS；S3 API 和管理控制台只绑定宿主机回环地址。
- 应用 S3 上传处理器完成合成录音上传、完整下载、Range 下载及匿名访问拒绝验证。
- 后端存储统计、对象列表、同源签名下载（含中文及特殊字符对象名）验证通过。
- 使用合成话单及无本地副本的 WAV 文件，验证录音播放、下载和 Range 请求。
- RustFS 重启后，测试对象和录音仍可读取；测试结束后仅删除本次合成数据。
- Chrome 登录存储管理页面，存储桶状态正常，统计及对象列表接口返回成功。
- 本轮不是实际通话验收；普通提示音和 Mock TTS 文件仍使用本地共享卷。

## 服务器部署与验收

- 服务器 `81.70.8.219`：通过用户指定 SSH 私钥部署；Docker Compose 实际构建和启动均已执行。
- 复用已有宿主机 Nginx，只新增 `/etc/nginx/conf.d/xhbt-demo.conf`；`nginx -t` 与平滑 reload 通过，不启动额外 web 容器。
- 公网 `http://81.70.8.219:18088/healthz` 返回 HTTP 200；登录页可访问。
- API 容器 `xhbt-demo-api-1` 和 FreeSWITCH 容器 `xhbt-demo-freeswitch-1` 均健康。
- 复用 `smbDemo` 共享网络、既有 `mysql8` 和 `redis`；新建 `xhbt_demo` 的 72 张表。
- Redis 10、11 已占用，演示版选择空闲的 **12**；未清理或覆盖已有库。
- 实际 API：登录、菜单、工单新增/更新/读回/分页、客户新增与筛选、号码转换规则、日期筛选、Mock ASR 与 TTS 落库全部通过。
- 后端容器重启后，工单和转写记录仍能读回。
- Chrome 经 SSH 加密通道测试登录、首页和 14 个业务页面，共 16 页，无 JS、HTTP 或 API 业务错误；检查桌面/移动截图。
- FreeSWITCH 1.11.3 及固定版本依赖源码均通过 SHA256 校验，并在目标服务器构建成功。
- ESL 认证、SIP internal profile 运行、后端节点在线验证通过。
- FreeSWITCH 引擎实际加载动态分机 1001/1002，密码与私有配置一致；动态拨号规则含内线互拨及 9196 回声规则。
- SIP/RTP 的容器绑定地址和对外通告 IPv4 分别核对；ESL 8021 不发布宿主机端口。
- 实际构建修复：指定 FreeSWITCH 配置/数据库/日志路径，移除上游默认配置，校正非 root 用户的文件权限；部署后重新验证通过。
- Maven 329 项，失败 0、错误 0、跳过 6；发布工具 15 项通过。

## 仍未验收

- 真实 SIP 终端的注册/互拨和 RTP 双向音频、录音播放、远程 WebRTC。
- 运营商中继、真实公网电话、云 ASR/TTS、并发容量或生产 SLA。
- 外部浏览器话务需要可信 HTTPS/WSS、终端可达媒体地址及按需开放的网络端口。

## 此前本机验收

- 从脱敏源码重新构建 Java 17 后端 JAR、Vite production 前端；不是复制现场配置的旧 target/dist。
- 独立 MySQL 8.4.2 与 Redis 环境：72 张表、合成菜单、管理员 BCrypt 哈希初始化成功。
- 非空数据库初始化拒绝；MySQL 账号权限拒绝越权创建其他库。
- 实际 API：登录、权限菜单、未绑定坐席返回空列表、工单新增/修改/查询/分页、客户创建/筛选/分页。
- 实际 MySQL：号码规则带引号 priority 列、字符串拼接筛选、操作日志日期过滤、客户相关 mapper 查询。
- MyBatis JDBC 识别 MySQL；客户人群命名参数、唯一约束与 duplicate-key upsert 重复写入后仅一行，事务回滚通过。
- Mock ASR：PCM 分帧输入、摘要/分段落库、按 callId 读回；Mock TTS：任务完成、文件记录读回。
- Windows 原生启动与停止脚本验证；重启后工单状态与 ASR 转写仍可读回。
- Chrome：登录、首页及 14 个业务页面，共 16 个页面检查，无 JS 异常、HTTP 错误或 API 业务错误；桌面与移动登录截图已检查。
- Maven 测试：326 项，失败 0、错误 0、跳过 6。跳过项不是实网呼叫验收。
- Docker Compose v2.39.4 官方二进制校验 SHA256 后，主配置与 init profile 均通过 `config` 校验。
- Compose 渲染验证：复用外部网络、不定义 MySQL/Redis 服务、API 和初始化器连接配置一致、演示库 xhbt_demo、Redis 库 10。
- Bash / PowerShell 语法检查通过；Docker 辅助 Java 初始化器与健康检查类编译通过，健康检查访问真实后端通过。
- 指定 YAML 仅提取主 MySQL 与 Redis；当前私有交付允许真实凭据仅进入 `docker/.env`，不进入镜像构建上下文、JAR 或公开源码 ZIP。

本记录区分配置/容器/业务验收与真实终端通话验收，不作为生产 SLA 或公网安全保证。
