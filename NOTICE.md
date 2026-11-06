# 来源与许可证说明

本工作区基于 [OpenCallHub](https://github.com/sxwdmjy/openCallHub) 开源项目进行维护。上游作者及源码中已有的作者、版权和许可证信息应继续保留。

- 后端许可证：随仓库提供的 [GNU GPLv3](LICENSE)。
- 公共依赖：`io.github.sxwdmjy:och-common:1.0.1`，其发布 POM 标明 GPL-3.0-only，二进制和源码均可从 Maven Central 获取。
- Kingbase JDBC：使用 Maven Central 的 `cn.com.kingbase:kingbase8:8.6.0`，发布 POM 声明 Apache-2.0；数据库服务端由使用者另行准备。
- 配套前端来源：上游 README 指向 [waihu-app](https://gitee.com/zhongjiawei999/waihu-app)，本机维护版本的许可证文件为 Apache-2.0；包含前端源码的发布包保留其独立 LICENSE。
- FreeSWITCH、Sofia-SIP、libks、spandsp 等第三方源码和二进制有各自许可证。本次公开源码包不包含工作区中的第三方源码树或安装包。
- 发布工具将前端铃声替换为数学合成的 PCM 提示音，不复制原铃声录音；其他随前端保留的界面素材沿用其工程来源与许可证，权利范围仍需维护者在公开发布前复核。

本工作区没有用于确认 Git 祖先关系的完整历史。公开上游提交的内容比对仅说明文件差异，不证明作者身份或准确派生起点。来源、版本快照及修改范围见 [来源记录](docs/open-source/provenance.md)。

GPLv3 本身允许商业使用和收费分发。原 README 关于“GPL 不允许商用”的文字与许可证本文存在冲突，本次文档修订予以纠正，并保留该冲突的来源说明。此修订不代表取得上游作者的额外商业授权，也不改变第三方组件许可证。
