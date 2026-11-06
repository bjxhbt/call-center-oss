# 私有部署包安全说明

仅名称带 `Private` 的交付包包含真实 `docker/.env`。该文件来自用户指定配置中的主 MySQL 和 Redis 节点，并追加分别生成的 FreeSWITCH、SIP 和 RustFS 演示密钥。

- **整个私有压缩包不得上传公共 Git 仓库、网盘或制品仓库。** 不要把 `.env` 截图发给第三方。
- 未复制其他项目的完整 YAML、从库连接、原业务数据、云服务账号或运营商信息。
- 新库为 `xhbt_demo`，Redis 默认 10；本次服务器检测后改用空闲库 12，不写原业务数据库，不执行 Redis 清库。
- 账号沿用指定配置。如果是数据库管理员账号，建议部署后替换为仅授权演示库的账号。
- `docker/.env` 由 Compose 运行时读取；两个镜像构建上下文均有白名单 `.dockerignore`，不会把它烘入镜像层。
- 压缩包是普通 ZIP，不提供密码加密。传输请使用受控渠道，解压后执行 `chmod 600 docker/.env`。
- `source/OpenCallHub-public-source.zip` 仍为脱敏源码；只允许分享该独立源码包，不代表整个私有部署包可公开。

本次服务器复用已有 Nginx：后续启动使用 `bash scripts/docker-start-host-nginx.sh`，停止使用 `bash scripts/docker-stop-host-nginx.sh`。已完成初始化，不要再次加 `--init`。
全新服务器首次部署使用 `bash scripts/docker-start-host-nginx.sh --init`，再运行 `bash scripts/install-host-nginx.sh`。Nginx 容器方式见 DOCKER.md。
管理员密码由首次部署时交互设置，不在压缩包中预置通用管理密码。

自动检测的服务器 IPv4 只适用于普通 Linux 局域网部署。多网卡或 NAT 环境，请把 `OCH_FS_PUBLIC_IP` 改为 SIP 终端可达的 IPv4，保留所有凭据不变。
默认监听 Web/SIP 所有网卡，请在宿主机和网络边界只允许可信演示网段。8021 不对宿主机发布。

实际容器及通话验收状态见 VALIDATION.md；已在目标服务器完成容器和 ESL 联通验证，尚未完成真实终端通话测试。
