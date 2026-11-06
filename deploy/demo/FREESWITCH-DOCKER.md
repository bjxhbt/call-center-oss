# FreeSWITCH 的 Docker 部署方式

**本交付版已加入 Docker Compose。** 使用本包的 Dockerfile 从上游固定版本源码构建，不依赖未知社区镜像。

FreeSWITCH 1.11.3、sofia-sip 和 spandsp 均固定源码版本及 SHA256；ESL、XML-Curl 和两路演示分机使用分别生成的随机密钥。

## 推荐拓扑

- Linux 宿主机：本版使用共享 Docker bridge 网络，并显式映射少量演示 SIP/RTP 端口。
- 信恒博通后端通过 `freeswitch:8021` 访问 ESL；FreeSWITCH 通过 `http://api:14320/fs/curl/api` 获取配置。
- XML-Curl 使用 Basic 认证，密码与后端一致；ESL 只在内部网络访问，限制 RFC1918 来源并校验独立随机密码。
- 后端先健康，FreeSWITCH 才启动，以保证动态 Sofia、分机、ACL 和拨号规则可加载。
- SIP/RTP 在容器内绑定本地地址，对终端通告 `OCH_FS_PUBLIC_IP`，不向终端暴露 Docker 服务名。
- `mysql8` / `redis`：继续复用现有容器。
- Nginx 托管构建后的前端，API 通过同源反向代理访问。

上游一般推荐 host 网络以减少媒体 NAT。本版为复用既有 Docker 网络采用桥接映射，适合隔离演示；多层 NAT、公网或高并发不属于本包验收范围。

Docker Desktop / WSL2 可用于试验，但 SIP NAT、双向音频和宿主机网络行为与原生 Linux 不同；正式通话验收推荐原生 Linux。

## 需要配置的网络

| 功能 | 常用端口 | 说明 |
| --- | --- | --- |
| Nginx 前端 | 8088/TCP | 仅向可信演示网段开放 |
| SIP | 5060/UDP、TCP | 本包映射的 internal profile，启用认证 |
| WebSocket 信令 | 5066/TCP | 仅 WS；远程浏览器还需要 HTTPS/WSS |
| ESL 控制 | 8021/TCP | 不映射宿主机端口，后端在 Docker 内部访问 |
| RTP 媒体 | 20000–20199/UDP | 已与 switch.conf 范围一致，演示限额不是并发性能承诺 |

5080、5061、5081 和 7443 未映射；无运营商中继和公开的免鉴权入口。不要直接将所有端口暴露公网。
服务器已有 FreeSWITCH 或 SIP 服务时，先检查端口冲突；不要只改 Docker 端口映射而不改 Sofia 通告端口。

还需配置正确的 `external_sip_ip`、`external_rtp_ip` 与本地网络 ACL；有 NAT 时检查 SDP 中地址、端口及双向媒体。
端口数取决于并发通话与媒体模式，不能只开放 Web 与 5060。

## 镜像与模块

构建清单包含 `mod_sofia`、`mod_event_socket`、`mod_xml_curl`、`mod_dptools`、`mod_sndfile`、PCMA/PCMU 及 Opus 等模块。
容器启动时根据环境生成只允许容器用户读取的 XML，真实密码不写入 Dockerfile 或镜像层。
`mod_audio_fork` / `mod_audio_stream` 不保证在基础镜像中自带；真实流式 ASR 需要额外编译或安装与 FreeSWITCH ABI 匹配的模块。

配置、日志与录音应挂载持久化目录；FreeSWITCH 写入的录音路径要能被后端读取，或配置可用的对象存储/文件服务。
本包默认的 Mock ASR/TTS 不依赖这些模块，不代表真实通话链路已经验证。

## 内线测试

1. 按 DOCKER.md 初始化并启动，确认脚本打印后端节点在线。
2. 用两个 SIP 软电话注册 `1001` 和 `1002`，服务器/域为 `OCH_FS_PUBLIC_IP`，端口 5060。
3. 各分机的密码在私有 `docker/.env` 的对应变量中，不是通用密码。不要将此文件公开。
4. 互拨 `1001` / `1002`，拨 `9196` 测试双向 RTP 回声。此步骤必须由目标环境实际验证。

管理员默认不绑定坐席；需要浏览器话务条时在系统内新建或绑定对应坐席。远程 HTTP 页面的麦克风和 WS 不等同于可用的 WebRTC。
公网/远程浏览器需要可信 HTTPS/WSS 证书、匹配域名及 NAT/ICE/TURN 规划；本包不生成伪装为受信任证书的文件，也不宣称 WebRTC 已打通。

## 官方依据

- [FreeSWITCH 官方 Docker README](https://github.com/signalwire/freeswitch/blob/master/docker/README.md)
- [SignalWire Docker 安装说明](https://developer.signalwire.com/freeswitch/FreeSWITCH-Explained/Installation/Docker/)
- [FreeSWITCH 官方 release Docker 文件](https://github.com/signalwire/freeswitch/tree/master/docker/release)

上述文档确认了 host 网络建议、配置卷、ESL、WebSocket 与 RTP 端口需求；实际镜像、线路和并发能力仍需在目标服务器验收。
