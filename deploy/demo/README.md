# 信恒博通 · MySQL 完整系统演示版

这是前后端真实运行、业务数据写入 MySQL 的演示包，不是静态页面或前端 Mock。
品牌为“信恒博通”，不包含原政务平台名称、客户数据、运营商账号、录音或云服务密钥。

**本次交付优先使用 Docker Compose，复用已有 `mysql8`、`redis` 容器。**
按 [DOCKER.md](DOCKER.md) 部署，包含 API、FreeSWITCH 和 RustFS；Nginx 可使用容器或现有宿主机服务。私有交付包的真实连接配置已放入 `docker/.env`。
`81.70.8.219` 服务器复用已有宿主机 Nginx，使用 `docker-start-host-nginx.sh`，不启动额外 Nginx 容器。
**名称带 `Private` 的包包含真实凭据，仅限私有服务器部署，禁止开源或公开转发。** 随包 `source/` 中的公开源码仍保持脱敏。
以下 JDK / Nginx / PowerShell 步骤是 Windows 原生运行的备用方式。

## 环境与内容

- 本次交付已在 Linux x86_64、Docker Compose、已有宿主机 Nginx、MySQL 8.4 和 Redis 环境验收。另保留已验收的 Windows 原生运行方式及 Windows Nginx。
- 建议演示主机：4 核 CPU、8 GB 内存、至少 5 GB 可用磁盘；这不是实际并发呼叫容量承诺。
- `app/och-api.jar`：重新编译的后端，内置 MySQL Connector/J。
- `web/`：正式构建的信恒博通界面，API 使用同源反向代理。
- `database/`：72 张表的全新 MySQL 建表脚本、纯合成管理配置。
- 菜单包含当前界面的系统、呼叫、语音、通信、客户、任务、智能服务与 FreeSWITCH 配置入口；Docker 初始化提供随机密码的演示 SIP 分机，不绑定管理员坐席。
- `scripts/`：Docker、宿主机 Nginx 和 Windows 原生运行的初始化、启动、停止脚本。
- `source/`：与二进制对应的脱敏源码、公共依赖源码及来源记录。
- `SHA256SUMS`：包内文件校验值。

## 1. 准备专用数据库

请用 MySQL 管理员创建**新的空库**及专用账号。示例中的密码必须自行替换，不要使用生产库：

```sql
CREATE DATABASE xhbt_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'xhbt_demo'@'127.0.0.1' IDENTIFIED BY '<自行设置数据库密码>';
GRANT ALL PRIVILEGES ON xhbt_demo.* TO 'xhbt_demo'@'127.0.0.1';
```

数据库不在本机时，将账号允许来源改为后端所在主机；不要直接使用 `%` 暴露数据库。
初始化所需 DDL 权限只授予此演示库。初始化后可按企业规范收回 DDL 权限。
MySQL 服务端使用官方的受维护 8.4 补丁版本；本机验证使用 8.4.2，不建议照搬旧补丁到公网。

## 2. 配置连接

在包根目录打开 PowerShell：

```powershell
Copy-Item config/demo.env.example config/demo.env
notepad config/demo.env
```

填写 MySQL、Redis 的地址、端口、账号等。默认 Redis 使用逻辑库 10，请确保未被其他系统占用。
数据库密码可在启动/初始化时交互输入；Redis 如配置认证，需设置 `OCH_REDIS_PASSWORD`。
本地 `config/demo.env` 可能含密码，不要回传、提交或再次打包分发。
连接 MySQL 时保留 `ANSI_QUOTES` 和 `PIPES_AS_CONCAT` 的连接级兼容配置，无需修改服务器全局 SQL 模式。

## 3. 初始化

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/init-db.ps1
```

按提示输入 `INITIALIZE xhbt_demo`，数据库密码，以及自行选择的管理员密码（至少 12 位）。
管理员账号为 `admin`，**没有通用初始密码**；密码仅保存 BCrypt 哈希。
初始化器遇到已有表的数据库立即拒绝执行，不删除、不覆盖已有数据。
MySQL DDL 会隐式提交，失败时不能整体回滚；请检查错误并新建空演示库后重试，不要手动在业务库清表。

## 4. 启动与停止

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stop.ps1
```

默认浏览器地址：`http://127.0.0.1:8088/login`。
默认后端端口 14320、文件端口 19527；后端仅绑定本机。启动脚本等待后端就绪后才开启页面。
脚本不会关闭占用端口的其他进程，也不会关闭 MySQL 或 Redis。
日志位于 `logs/`，进程记录位于 `runtime/`。重启前后业务数据保留在 MySQL；重启不会重新初始化库。
局域网演示需设置 `OCH_WEB_BIND_ADDRESS=0.0.0.0` 和 `OCH_PUBLIC_HOST`，并仅向可信网络放行 Web 端口。
不要将 JAR、数据库、Redis 或文件端口直接开放到公网；远程 WebRTC 还需要 HTTPS。

## 演示范围与限制

- 可演示：真实账号登录、权限菜单、工单保存/查询/修改、客户与管理配置、IVR 编辑、ASR/TTS 管理与 Mock 链路。
- ASR/TTS 默认 `MOCK`，不调用收费云服务；Mock 转写是合成文本，Mock TTS 不代表真实语音效果。
- Docker 版已配置 FreeSWITCH、ESL、XML-Curl 和内线演示规则；不包含中继、运营商线路或媒体网关，不能直接拨打公网电话号码。
- Docker 演示版使用独立 RustFS 存储桶，已验证合成录音的上传、下载与重启持久化；真实终端通话录音仍需验收。Windows 原生运行方式需另行配置对象存储。
- Quartz 自动调度关闭，避免演示时意外触发外呼；不宣称真实外呼、WebRTC、录音或云 ASR 已验收。
- 本版本用于隔离演示，不是生产加固版；包括依赖中的既有 JWT 签名约定，公网部署前仍需完成密钥配置化、安全评审和权限验收。
- 改动保留上游 OpenCallHub 及第三方许可证与来源，不代表软件原始版权变更。

## 重新构建

解压 `source/OpenCallHub-public-source.zip`，使用 JDK 17、Maven、Node.js：

```text
mvn -B -pl och-api -am package -DskipTests
cd frontend
npm ci
npm run build
```

后端从随包的 MySQL 配置构建；运行时仍优先使用外部 `config/application.yml`。
源码导出工具和构建记录用于追溯本次包，第三方来源见 `THIRD-PARTY.md`。
