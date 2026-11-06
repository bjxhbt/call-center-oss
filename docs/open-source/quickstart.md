# 公开环境快速开始

本入口使用独立数据库和公开配置，适用于开发和复现。目标数据库为 KingbaseES V8R6；初始化保留 Kingbase 方言，未宣称可直接用于 MySQL。

## 1. 准备代码与构建工具

使用公开源码包中的对应快照。后端需要 JDK 17 和 Maven 3.9，前端需要 Node.js 20 LTS 与 npm。依赖和源码包获取方式见 [依赖说明](dependencies.md)。

在源码包根目录构建 API 及其模块：

```bash
mvn -pl och-api -am package -DskipTests
```

首次构建需要联网获取 Maven 依赖。`-DskipTests` 用于先生成启动产物；自动测试和运行验收分别执行，不能把打包成功当作业务测试通过。

## 2. 创建独立数据库并初始化

在 KingbaseES 中创建空白数据库 `openCallHub_demo`，连接账号需具有该数据库中 `public` schema 的建表权限。不要将下面的初始化入口指向已有业务库；脚本发现 `public` 中已有基础表会中止。

初始化文件位于 `deploy/public/`：

| 文件 | 内容 |
| --- | --- |
| `00-init.sql` | 空库检查、参数检查与事务入口 |
| `01-schema.sql` | 66 张基础表的纯结构，已移除行数据、属主、授权和注释，并将 ID 字段扩展为 bigint |
| `02-upgrade.sql` | 现有增量结构，包括转写、TTS、工单和号码转换规则 |
| `03-demo.sql` | 新建管理员、角色、菜单根节点、IVR 分类和演示技能组 |
| `04-menus.sql` | 当前扩展菜单及管理员菜单关联 |

没有复制历史管理员密码、SIP 密码、云凭证、客户、录音或话单。初始化管理员密码由运行者自行设置，SQL 只接收本次生成的 BCrypt 哈希。

以下为 PowerShell 示例，`mvn`、`java`、`ksql` 应已在 PATH 中：

```powershell
$securePassword = Read-Host 'Demo administrator password (at least 12 characters)' -AsSecureString
$env:OCH_BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
try {
    $mavenRepo = (& mvn help:evaluate '-Dexpression=settings.localRepository' -q '-DforceStdout' '-Dstyle.color=never' | Select-Object -Last 1).Trim()
    $bcryptJar = Join-Path $mavenRepo 'org/springframework/security/spring-security-crypto/6.3.1/spring-security-crypto-6.3.1.jar'
    $adminHash = & java --class-path $bcryptJar tools/open-source/HashPassword.java
    if ($LASTEXITCODE -ne 0) { throw 'Password hashing failed' }
} finally {
    Remove-Item Env:OCH_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
}
& ksql -h 127.0.0.1 -p 54321 -U system -d openCallHub_demo -W -v "admin_hash=$adminHash" -f deploy/public/00-init.sql
if ($LASTEXITCODE -ne 0) { throw 'Database initialization failed' }
```

`ksql -W` 会交互询问数据库密码。登录应用的初始用户名为 `admin`，密码为刚才设置的密码。若 Maven 使用自定义本地仓库，以 `settings.localRepository` 返回值为准。

验证初始化后只存在演示数据：

```sql
SELECT user_name, status FROM sys_app_user;
SELECT COUNT(*) FROM fs_config;
SELECT COUNT(*) FROM customer;
SELECT COUNT(*) FROM call_record;
```

后三项应为 0。初始化创建的技能组没有坐席成员，不会自动发起电话连接。

## 3. 配置 Redis 和 API

准备一个可用的 Redis 实例。公开配置默认使用 Redis 数据库 10；确认该编号可用于本次演示，或通过 `OCH_REDIS_DATABASE` 指定独立编号。

在同一 PowerShell 会话设置连接信息：

```powershell
$env:OCH_DB_URL = 'jdbc:kingbase8://127.0.0.1:54321/openCallHub_demo'
$env:OCH_DB_USER = 'system'
$dbPassword = Read-Host 'Database password' -AsSecureString
$env:OCH_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPassword).Password
$env:OCH_REDIS_HOST = '127.0.0.1'
$env:OCH_REDIS_PORT = '6379'
$env:OCH_REDIS_DATABASE = '10'
java -jar och-api/target/och-api-0.0.1.jar --spring.config.location=file:./deploy/public/application.yml
```

Redis 有密码时同时设置 `OCH_REDIS_PASSWORD`。`spring.config.location` 使用这份完整公开配置，避免合并工作区里原有的现场配置。服务默认绑定 `127.0.0.1:4320`，公开配置通过必要环境变量注入数据库密码。

浏览器打开 `http://127.0.0.1:4320/swagger-ui/index.html`，或请求 `GET /v3/api-docs`，确认服务已启动。业务 API 除明确允许的路径外需要登录。

公开配置使用内存 Quartz 且默认不启动调度，ASR/TTS 为 Mock，音频推流和真实 TTS 播放关闭。S3 参数为本地样例，使用上传录音功能前需配置实际对象存储服务、凭证和 bucket。API 内置文件服务还会使用端口 9527，可通过 `OCH_FILE_PORT` 修改。

## 4. 启动配套前端

发布包包含前端时，在 `frontend/` 中执行：

```bash
npm ci
npm run dev -- --host 127.0.0.1 --port 3008
```

本地独立维护前端时，将 `deploy/public/frontend.env.example` 放到前端根目录并命名为 `.env.development.local`，再执行上面的命令。这个文件的开发环境优先级高于 `.env.development`。前端代理目标为 `http://127.0.0.1:4320`，业务模块使用真实 API。

访问终端输出的本地 URL；端口被占用时使用实际输出地址。用初始化的 `admin` 账号登录，进入 IVR 和语音服务界面。

生产构建使用 `npm run build`，生产环境应由反向代理共同转发业务 API 与 `/ws`，电话注册还需单独配置 SIP WebSocket 和浏览器安全上下文。

## 5. 联调与验收

按 [演示说明](../../examples/public-demo/README.md) 导入草稿流程，执行 Mock 检查，并为真实通话配置 FreeSWITCH、坐席及路由。Mock 检查会写入演示转写记录，应在本次独立数据库中执行。

本次是否完成空库导入、实际启动和真实通话，以 [验证记录](verification.md) 为准。需要使用国产应用服务器的现场部署时，再按实际部署版本调整 WAR、上下文与外部配置，不直接套用本机端口。
