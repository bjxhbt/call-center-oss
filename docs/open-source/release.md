# 公开源码包导出与校验

使用 Python 3.9 或以上，在维护工作区根目录执行。输出目录必须尚不存在，避免覆盖已审阅的快照。

```bash
python tools/open-source/export_release.py --frontend /path/to/waihu-app --output outputs/open-source/release-candidate
python tools/open-source/verify_release.py outputs/open-source/release-candidate/OpenCallHub-public-source.zip
```

不带 `--frontend` 时只导出后端；需要完整配套界面时应指定对应维护版本。发布工具按文件范围取当前工作区内容，包含尚未提交的修改，因此每份包通过校验值固定版本，不以 SVN 根修订号代替文件快照。

## 产物

| 产物 | 用途 |
| --- | --- |
| `OpenCallHub-public-source.zip` | 可审阅的源码候选包 |
| 外层 `SHA256SUMS` | ZIP 文件的 SHA-256 |
| `release-report.json` | 文件数、脱敏后的扫描发现、是否创建 ZIP |
| `source/source-manifest.json` | 输入与导出 SHA-256，以及配置替换、地址调整、合成资源和排除文件记录 |
| 包内 `SHA256SUMS` | 逐文件完整性校验 |

校验脚本检查外层与包内哈希、源码清单一致性、必需文件、路径和不应出现的运行产物。它校验内容完整性，不提供作者身份或数字签名证明。

## 导出范围与处理

包含后端根和模块 POM、模块 `src`、许可证、公开文档、初始化与示例工具。前端只包含 `src`、`public` 和明确列出的构建配置及锁文件。保留源码中的原有作者声明和前端 LICENSE。

公开副本使用 `deploy/public/` 替换 API、文件客户端和 MRCP 的配置。根 README 使用公开入口版本。源码中的私网 IPv4 地址和本机工作区路径转换为本机演示地址，测试文件中的非样例密码替换为合成字符串，变换路径均写入清单。前端铃声由工具生成 PCM 提示音，不复制来源未单独确认的录音。

现场部署树、数据库备份、Redis 数据、日志、客户数据、历史语音、第三方安装树、构建产物、依赖缓存及 `.bak` 等备份文件不进入候选包。维护工作区的原始文件保留。

凭据检查覆盖常见带引号与不带引号的配置、常见云密钥格式和私钥头；报告只写文件、行号和类型。发现未处理项时不会创建 ZIP。模式检查有覆盖边界，公开发布前仍需依据清单复核素材来源及许可。

## 复核与验证

在 `source/` 副本按 [快速开始](quickstart.md) 构建和配置，不将维护工作区里的现场文件复制回候选包。当前已执行与待环境验证的项目见 [验证记录](verification.md)。

本次任务仅生成本地候选产物，没有推送远程仓库或对外发布。
