# 本次验证记录

日期：2026-10-15。此记录将文件与工具检查、应用自动测试以及现场验收分别说明。

## 已执行

| 验证项 | 结果与范围 |
| --- | --- |
| Maven Central 公共依赖 | `och-common:1.0.1` POM、运行 JAR 和源码 JAR 可获取；运行包 267219 字节，源码包 108776 字节 |
| 上游文件比对 | 对固定参考提交生成 `upstream-diff.json`：新增 302、修改 157、相同 514、本地缺失 2；不能推断 Git 祖先关系或作者归属 |
| 后端现有自动测试 | Maven 3.9.9、JDK 17，执行 `mvn -o -B -pl och-api -am test -Dstyle.color=never`，BUILD SUCCESS |
| 公开数据库结构提取 | 从已有 custom-format 备份执行 schema-only 导出，共 66 张基础表；未提取业务行数据、属主、授权或注释 |
| 公共 JDBC 驱动 | 原 `com.kingbase8:kingbase8:8.6.0` 的 Central POM 返回 404；改用可公开获取的 `cn.com.kingbase:kingbase8:8.6.0`，JAR 包含 `com.kingbase8.Driver`，修改后后端打包通过 |
| 发布工具与评估指标测试 | 11 项通过，包含凭据报告不暴露值、带引号与不带引号配置、重复输出保护、合成铃声、Mock 隔离、失败样本计入 CER 和非法指标拒绝 |
| 管理员密码生成 | JDK 17 配合单独的 `spring-security-crypto:6.3.1` 生成符合格式的 60 字符 BCrypt 哈希，无需额外日志依赖 |
| 公开配置与 IVR 表达式 | 两份 YAML 经 SnakeYAML 解析；按当前 FlowFunctions/FlowExpression 验证工作时间表达式，0 至 23 小时全部符合分支预期 |
| 初始化兼容结构冒烟 | PGlite 0.3.14 内存环境按顺序执行 4 份 SQL，得到 72 张表；演示管理员/角色/技能组各 1 条，FS、客户、话单和转写表均为 0，菜单父节点无缺失 |
| PowerShell 脚本 | 两个脚本通过 AST 解析；用本地桩响应检查 Mock 成功路径、失败/空文本拒绝和真实 Provider 缺失拒绝，未请求实际演示 API |
| 公开副本后端构建 | 在独立导出的 `source/` 中执行 `mvn -o -B -pl och-api -am package -DskipTests -Dstyle.color=never`，10 个 reactor 项目通过；随后公共 JDBC 坐标修改也完成打包验证 |
| 配套前端构建与变更验证 | 导出副本使用 Node.js 25.5.0、`npm ci` 和 `npm run build`，构建成功；SIP 测试 8 项通过，验证合成测试密码替换未破坏该模块。存在原有深度选择器和大分块告警 |

后端测试汇总：

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| och-file | 1 | 0 | 0 | 1 |
| och-system | 61 | 0 | 0 | 0 |
| och-security | 4 | 0 | 0 | 0 |
| och-esl | 52 | 0 | 0 | 0 |
| och-ivr | 5 | 0 | 0 | 0 |
| och-ai | 135 | 0 | 0 | 3 |
| och-call-task | 4 | 0 | 0 | 0 |
| och-api | 64 | 0 | 0 | 1 |
| 合计 | 326 | 0 | 0 | 5 |

上述是当前工作区测试结果。其中包含依赖现有基础设施的集成测试；它们没有验证本次公开初始化脚本，也不代替真实 SIP 通话和语音引擎验收。

初始化兼容检查仅在测试副本中移除了 Kingbase 的 `SET escape` 和字符长度 `char` 修饰，并将 `blob/tinyint` 映射为 PostgreSQL 类型。它可以检查 SQL 顺序、字段、种子数据及空库保护；没有验证 Kingbase 方言和客户端 `ksql` 元命令。正式初始化文件保留 Kingbase 方言。

源码候选包随附逐文件清单与 ZIP 校验值，按 [导出与校验说明](release.md) 检查；生成包时替换现场配置并排除源码备份。哈希校验范围以当次实际包内清单为准。

## 需要环境条件的项目

| 项目 | 当前状态 | 完成所需条件 |
| --- | --- | --- |
| Kingbase 空库实际初始化、登录和页面联调 | 待独立环境验证 | 可用的隔离 Kingbase 数据库、Redis 和对应前端 |
| 实际运行 Mock 检查脚本 | 已提供脚本，待演示 API 启动后执行 | 初始化后的演示 API 与管理员令牌 |
| 真实通话与录音 | 待验证 | FreeSWITCH、测试 SIP 终端或线路、技能组成员及存储 |
| 真实 ASR/TTS 效果与故障隔离 | 待验证 | 实际 Provider、获授权测试音频与通话样本 |
| 原始派生基准及额外商业授权 | 当前材料不能确认 | 原始版本历史或权利人明确说明 |

公开源码包中的 `source-manifest.json`、`SHA256SUMS` 和外层 `release-report.json` 用于审阅本次实际打包内容。运行证据缺失的项目继续保持待验证。
