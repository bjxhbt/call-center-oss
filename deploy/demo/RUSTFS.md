# RustFS 对象存储

本演示版独立部署 RustFS 1.0.4，镜像以 SHA256 固定版本。使用专用数据卷和 `xhbt-demo-recordings` 存储桶，不复用其他项目的 RustFS 数据或凭据。

## 连接与数据

- 后端使用 Docker 内部地址 `http://rustfs:9000`，凭据由 `docker/.env` 的 `OCH_RUSTFS_ACCESS_KEY`、`OCH_RUSTFS_SECRET_KEY` 注入。
- S3 API 仅映射宿主机 `127.0.0.1:19002`；管理控制台仅映射 `127.0.0.1:19003`。
- 数据与日志分别保存到 Compose 命名卷 `rustfs-data`、`rustfs-logs`。日常停止不删除数据卷。
- 不启用匿名读写。浏览器下载通过已有 Nginx 的 `/object-storage/` 路径转发，保留签名所需的上游 Host 与对象路径。
- 下载链接由已登录的业务 API 生成，有效期一小时；该链接本身包含临时访问授权，不要公开分享。

录音上传服务使用 S3 接口保存录音，并由业务 API 提供同源流式播放和下载。普通语音提示文件及 Mock TTS 文件继续保存在 API / FreeSWITCH 的共享本地卷，供本地放音使用。

## 启停与校验

宿主机 Nginx 方式使用 `bash scripts/docker-start-host-nginx.sh` 和 `bash scripts/docker-stop-host-nginx.sh`；容器 Nginx 方式使用对应的 `docker-start.sh` 和 `docker-stop.sh`。启动脚本会创建专用桶并验证文件读写，遇到已存在的桶不清空数据。

在包根目录执行：

```bash
export OCH_HOST_NGINX=true
source scripts/docker-common.sh
compose ps rustfs
compose logs --tail=100 rustfs
compose --profile init run --rm --no-deps verify-storage
```

校验器调用应用实际的 S3 上传处理器，验证文件上传、完整下载、Range 下载和匿名访问拒绝；结束后只删除本次生成的测试对象。

控制台通过 SSH 本地转发访问，例如将本机端口 `19003` 转发到服务器 `127.0.0.1:19003`，再打开 `http://127.0.0.1:19003`。登录使用私有 `.env` 中的 RustFS 凭据。

## 重新准备配置

已有私有配置升级时，为 RustFS 追加独立随机凭据，保留既有 MySQL、Redis 和 FreeSWITCH 设置。新配置可使用公开源码中的工具生成：

```bash
python tools/open-source/prepare_docker_env.py --config /private/application-cloud.yaml --output /private/docker.env --with-freeswitch --with-rustfs
```

本包是单机演示部署，命名卷不能替代备份。需要生产使用时，应另行规划容量、备份与可用性。具体实测状态见 [验收记录](VALIDATION.md)。
