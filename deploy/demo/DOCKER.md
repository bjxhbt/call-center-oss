# Docker 部署：复用现有 mysql8 / redis

本次服务器复用已有宿主机 Nginx、MySQL 和 Redis，启动 API、FreeSWITCH 和独立 RustFS 容器。
按下面的“复用现有 Nginx”方式操作；另外提供 Nginx 容器方式用于其他服务器。
优先使用 Linux x86_64、Docker Engine、Compose v2（建议 2.24+）；部署主机需能拉取 Eclipse Temurin 和 Debian 镜像，使用 Nginx 容器方式时还需拉取 Nginx 镜像。
不需要 Maven、Node.js 或宿主机 Java。首次构建安装随包后端，并从固定版本上游源码编译 FreeSWITCH。
包内附带经过 SHA256 校验的 FreeSWITCH 及依赖源码；构建仍需访问容器镜像仓库和 Debian 软件源。未附带上游源码时会从 GitHub 下载。包内不含离线容器镜像。

## 私有配置

私有交付包已从指定 `application-cloud.yaml` 的主数据源和 Redis 节点提取连接信息，放在 **`docker/.env`**。
没有复制其他项目的完整配置，也没有把其数据库名作为演示库使用。
它包含真实数据库和 Redis 密码，以及新生成的 FreeSWITCH、演示分机和 RustFS 密钥，不能公开分发。
只有名称带 `Private` 的压缩包包含此文件；公开源码包不包含任何真实连接凭据。
也可通过 `OCH_DOCKER_ENV_FILE=/绝对路径/docker.env` 指向包外文件。

- MySQL 主机别名沿用 `mysql8`，数据库使用新库 `xhbt_demo`。
- MySQL 账号与密码沿用指定配置；如该账号为 root，生产化时应另建仅限演示库的账号。
- Redis 地址、端口、密码沿用指定配置；原项目用库 3，本次私有配置选择空闲库 **12**，通用模板默认库 10。
- 初始化前自动检查所选 Redis 库为空；若已占用，请在私有配置中选择空闲 Redis 库。
- 网络默认 `auto`：脚本检查运行容器的网络别名，寻找同时有 `mysql8` 和 `redis` 的唯一网络。
- 无法自动确定时，将 `OCH_DOCKER_NETWORK` 改为实际共享网络名。无需重启或重建原 MySQL、Redis。
- `OCH_FS_PUBLIC_IP=auto`：Linux 启动脚本读取默认出口网卡的 IPv4；多网卡/NAT 服务器必须改为终端可达的真实服务器 IPv4。
- 私有包默认 Web、SIP 监听 `0.0.0.0`，请仅向可信演示网段放行端口。ESL 8021、MySQL、Redis 不由本包发布；宿主机 Nginx 方式只在回环地址映射 API。

手工核对可使用 `docker network ls` 与 `docker inspect <MySQL容器> <Redis容器>`。
不要把 `mysql8` 当成宿主机域名；它仅在对应 Docker 网络内可解析。

## 首次启动

### 复用现有 Nginx

本次 `81.70.8.219` 使用已有宿主机 Nginx，独立站点 `/etc/nginx/conf.d/xhbt-demo.conf`，前端位于 `/home/nginx/html/xhbt-demo/web`。
后端仅映射 `127.0.0.1:14320`，Nginx 在 `18088` 提供同源页面/API；不启动 web 容器，不占用已有 `8088`。
本次服务器共享网络为 `smbDemo`，演示库 `xhbt_demo`，Redis 库 **12**（库 10、11 已被其他项目占用）。

```bash
bash scripts/docker-start-host-nginx.sh --init
bash scripts/install-host-nginx.sh
```

后续启动不加 `--init`；停止使用 `bash scripts/docker-stop-host-nginx.sh`。
日志命令前设置 `export OCH_HOST_NGINX=true`。宿主机 Nginx 仅 reload，不停止已有站点。
首次安装后应执行 `nginx -t`；安装脚本遇到内容不一致的既有同名站点文件会拒绝覆盖。
新端口需要在云安全组放行。内线终端测试另需按需放行 SIP 5060、WS 5066 和 UDP 20000–20199，仅允许可信来源。

### 使用 Nginx 容器

在包根目录执行：

```bash
bash scripts/docker-start.sh --init
```

输入 `INITIALIZE` 并设置管理员密码（至少 12 位）。脚本会：

1. 构建后端与 FreeSWITCH 镜像，复用已有 Docker 网络。
2. 验证所选 Redis 库为空，创建新的 MySQL 演示库并初始化 72 张表和合成菜单配置。
3. 写入本包管理的 FreeSWITCH 节点、随机密码分机 1001/1002、内部分机互拨与 9196 回声拨号规则。
4. 等待 API、FreeSWITCH SIP profile 健康，再启动 Nginx，并验证 ESL 认证和后端节点在线状态。
5. 启动独立 RustFS，创建专用存储桶并验证对象上传和读取。

首次 FreeSWITCH 源码编译可能需要较长时间；后续未修改构建文件时复用 Docker 缓存。
后续启动不再初始化业务表，不覆盖在界面中编辑的 SIP 分机和拨号规则；只同步本包管理的 ESL 节点密码。
首次 SIP 初始化要求分机、拨号规则和 ACL 表为空；存在未由本包管理的 FreeSWITCH 节点时拒绝覆盖。需迁移既有电话配置时先人工评审，不自动合并。

已有表的 MySQL 库会被拒绝初始化；MySQL DDL 隐式提交，失败时不会自动删表，需检查错误并使用新空演示库。
不会导入原项目数据，也不会执行 FLUSHDB、FLUSHALL、DROP DATABASE 或删除既有容器。

访问 `http://<服务器IP>:8088/login`，账号 `admin`，密码为刚设置的值。
服务器局域网演示时，在私有配置中设置 `OCH_WEB_BIND_ADDRESS=0.0.0.0`、`OCH_PUBLIC_HOST=<服务器地址>`，并只向可信网络放行 Web 端口。

## 后续启停与日志

```bash
bash scripts/docker-start.sh
bash scripts/docker-stop.sh
bash scripts/docker-logs.sh
```

第一次使用 `auto` 网络时，后续直接调用 Compose 的命令也需显式提供真实网络名，例如：

```bash
export OCH_DOCKER_NETWORK="$(bash scripts/detect-network.sh)"
export OCH_FS_PUBLIC_IP='<终端可达的服务器IPv4>'
```

MySQL、Redis 均是外部容器，不在本 Compose 的启停范围内；文件、录音、FreeSWITCH 数据和日志使用命名卷。
录音与放音文件在 API 和 FreeSWITCH 两个容器使用同一路径，写入 UID 均为 10001。
不要使用 `down -v` 作为日常停止命令。

## FreeSWITCH

已经加入 Compose，含固定版本源码构建、模块清单、XML-Curl 鉴权和 ESL 连接配置。
详见 [FREESWITCH-DOCKER.md](FREESWITCH-DOCKER.md)。不包含运营商中继或真实云语音账号；ASR/TTS 仍默认 Mock。
2026-10-26 已在目标 Linux Docker 服务器完成镜像构建、容器健康、ESL 认证和动态 SIP 配置验证；真实终端通话仍需单独验收，详见 VALIDATION.md。

## RustFS

使用独立容器、数据卷和 `xhbt-demo-recordings` 存储桶；S3 API 和管理控制台仅绑定宿主机回环地址。录音对象走应用 S3 接口，普通提示音和 Mock TTS 文件仍保存在共享本地卷。连接、启停和校验见 [RUSTFS.md](RUSTFS.md)。
