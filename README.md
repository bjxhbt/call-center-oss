# OpenCallHub 公开源码快照

面向私有化部署的 Java 呼叫中心与语音流程编排平台，包含 IVR 扩展、FreeSWITCH 业务集成及 ASR/TTS 接入实现。

本快照保留上游署名与许可证，使用公开配置和合成演示数据，附有逐文件校验值。真实通话、识别效果及现场部署的验证状态见发布记录。

- [软件介绍：信恒博通呼叫中心](docs/open-source/呼叫中心软件介绍.md)
- [准备状态与交付清单](docs/open-source/README.md)
- [构建、数据库初始化与启动](docs/open-source/quickstart.md)
- [依赖与前端版本](docs/open-source/dependencies.md)
- [IVR 与通话示例](examples/public-demo/README.md)
- [语音效果评估方法](docs/open-source/asr-evaluation.md)
- [来源与修改说明](docs/open-source/provenance.md)
- [许可证说明](NOTICE.md)
- [本次验证记录](docs/open-source/verification.md)
- [导出范围与完整性校验](docs/open-source/release.md)

后端要求 JDK 17、Maven 3.9，以及 KingbaseES V8R6 和 Redis。配套前端位于 `frontend/`（打包时指定前端工程才包含），使用其 `package-lock.json` 配合 `npm ci` 安装依赖。

公开示例默认启用 Mock ASR/TTS。Mock 用于接口联调；真实语音效果与呼叫业务需要另外配置和验收。
