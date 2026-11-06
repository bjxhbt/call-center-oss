# 公开依赖与版本

## 后端与公共模块

| 项目 | 当前版本或来源 |
| --- | --- |
| JDK | 17 |
| Maven | 本次验证使用 3.9.9 |
| 后端父工程 | `com.och:openCallHub:0.0.1` |
| Spring Boot | 3.5.3 |
| 数据库 | KingbaseES V8R6；本公开初始化保留 Kingbase 方言 |
| JDBC 驱动 | `cn.com.kingbase:kingbase8:8.6.0`，Maven Central 公开发布 |
| Redis | 与项目 Jedis 接入兼容的实例；公开配置默认使用数据库 10 |
| 公共模块 | `io.github.sxwdmjy:och-common:1.0.1` |

`och-common` 已发布到 Maven Central，不需要私有仓库。根 POM 统一管理版本，子模块已声明依赖。正常联网构建会下载它。

Kingbase 驱动已从依赖本机安装的 `com.kingbase8:kingbase8:8.6.0` 改为公共坐标 `cn.com.kingbase:kingbase8:8.6.0`，驱动类仍为 `com.kingbase8.Driver`。其 [发布 POM](https://repo.maven.apache.org/maven2/cn/com/kingbase/kingbase8/8.6.0/kingbase8-8.6.0.pom) 声明 Apache-2.0；驱动可下载不代表数据库服务端已安装或获许可使用。

```bash
mvn org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get -Dartifact=io.github.sxwdmjy:och-common:1.0.1 -Dtransitive=false
mvn org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get -Dartifact=io.github.sxwdmjy:och-common:1.0.1:jar:sources -Dtransitive=false
```

PowerShell 中将每个 `-D...` 参数用单引号包裹。Maven 不在 PATH 时，通过 `& '实际安装目录/bin/mvn.cmd'` 调用。

- [运行 JAR](https://repo.maven.apache.org/maven2/io/github/sxwdmjy/och-common/1.0.1/och-common-1.0.1.jar)
- [源码 JAR](https://repo.maven.apache.org/maven2/io/github/sxwdmjy/och-common/1.0.1/och-common-1.0.1-sources.jar)
- [发布 POM](https://repo.maven.apache.org/maven2/io/github/sxwdmjy/och-common/1.0.1/och-common-1.0.1.pom)

源码 JAR 解压后可阅读 Java 源码。它是发布源码快照；要修改公共模块，应保留原许可证，使用独立版本发布修改产物，并更新主工程的 `och-common.version`，避免用改动后的 JAR 冒充原有版本。

## 前端获取和版本对应

上游 README 提供的前端地址是 [waihu-app](https://gitee.com/zhongjiawei999/waihu-app)。该地址用于追溯来源，当前维护版本包含本地扩展，不能假定重新克隆上游即可得到同一版本。

本次核对的前端工程包名为 `nb-app`，版本字段为 `0.0.0`；SVN 工作副本根修订为 r483，包含未提交修改。当前后端根修订为 r490，同样包含本地修改。两个修订号是各自工作副本信息，不是可用于 Git checkout 的发布标记。

为准确提供配套代码，发布工具支持将当前前端源码一并放入包内的 `frontend/`。`source-manifest.json` 记录每个输入文件及导出文件的 SHA-256，`SHA256SUMS` 固定整个公开快照；该快照用于复现对应版本。

```bash
python tools/open-source/export_release.py --frontend /path/to/waihu-app --output outputs/open-source/release-candidate
```

前端建议使用 Node.js 20 LTS 和 npm，按随包的 `package-lock.json` 执行 `npm ci`。`package.json` 中保留了原有 pnpm 配置；本公开说明统一采用 npm。当前依赖包含 Vue 3、Vite 5、LogicFlow 和 JsSIP，完整版本由锁文件确定。

## 离线获取

离线环境需要同时准备 Maven 依赖缓存和前端 npm 缓存。单独复制 `och-common` JAR 无法满足所有传递依赖。应在联网环境完成相同版本的构建，再迁移完整缓存或使用组织内部 Maven/npm 镜像。禁止把已有现场数据库备份用作公开初始化数据。
