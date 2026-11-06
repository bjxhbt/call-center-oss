# IVR 与语音公开示例

本示例提供工作时间分流流程和可执行检查脚本。目标场景是“来电进入 IVR、工作时间转技能组、接听后录音与转写”；真实执行证据应与步骤一同保存。

## 1. 准备条件

先完成 [独立环境初始化](../../docs/open-source/quickstart.md)。演示数据提供分类 ID 1 和技能组 ID 1。初始化不创建 FreeSWITCH 连接、SIP 注册账号或真实录音。

登录后从浏览器的已认证请求中取得管理员访问令牌，并在 PowerShell 会话设置 `OCH_ACCESS_TOKEN`。该变量只供脚本发送 Authorization 头，不写入结果文件。

## 2. 导入工作时间流程

```powershell
& ./tools/open-source/import_demo.ps1 -BaseUrl http://127.0.0.1:4320 -GroupId 1 -SkillId 1
```

脚本调用 `POST /call/v1/ivr/add`，将 [流程 JSON](ivr-business-hours.json) 作为 `flowData` 字符串导入，创建草稿。再次运行会创建另一份草稿，应通过 IVR 列表确认需要使用的流程。

流程包含 6 个节点：开始、读取当前小时、条件分支、转技能组、非工作时间挂机和结束。条件为应用服务器本地时间 `09:00 <= 当前小时 < 18:00`。示例不包含工作日或节假日判断，也不依赖收号和转人工空实现节点；转接使用已经实现的转接节点及技能组路由。

打开草稿，确认节点与分支后发布。真实演示前将技能组绑定已注册且空闲的测试坐席，并把测试呼入号码路由到发布后的 IVR ID。技能组没有成员时预期无法转接，不能作为接通成功的证据。

## 3. 无电话环境下检查 Mock

确认 `GET /v3/api-docs` 可访问，管理员已登录，ASR 状态中的 `resolvedProvider` 为 `MOCK`：

```powershell
& ./tools/open-source/check_runtime.ps1 -BaseUrl http://127.0.0.1:4320 -Mode Mock
```

该检查投递一秒静音 PCM，通过模拟接口触发会话与持久化，再按 `callId` 读回摘要。它用于验证音频接入、Mock 会话和记录查询，不测量真实识别效果，不产生真实电话，也不替代 WebSocket 界面的人工确认。

## 4. 真实通话验收步骤

在隔离测试环境中准备 FreeSWITCH、两个测试 SIP 终端或一个测试坐席和一条获授权线路。配置应用中的 FreeSWITCH 服务器记录、坐席、技能组成员与测试号码路由，使应用能够接收 ESL 事件并向已注册坐席发起呼叫。

使用真实语音引擎时，启用对应 Provider 并注入其凭证，设置 `OCH_AUDIO_STREAM_ENABLED=true`、可达的 `OCH_ASR_AUDIO_STREAM_ENDPOINT` 和 `OCH_ASR_STREAM_TOKEN`。流地址需能被 FreeSWITCH 访问，`127.0.0.1` 只适用于服务位于同一主机的情况。根据所选模块加载 `mod_audio_fork` 或调整配置为已支持的 `mod_audio_stream`。

```powershell
& ./tools/open-source/check_runtime.ps1 -BaseUrl http://127.0.0.1:4320 -Mode LiveReadiness
```

该检查只确认 Provider、ESL 命令通道和音频推流前置条件。返回就绪后仍需完成下面的实际操作：

| 场景 | 操作 | 预期证据 |
| --- | --- | --- |
| 工作时间分流 | 从测试终端呼入已配置 IVR 的号码 | 对应 `callId` 的工作时间条件命中、目标技能组与坐席振铃记录 |
| 接听桥接 | 坐席接听并保持双向讲话 | 两条通道关联、桥接事件、双方可听见声音 |
| 通话录音 | 说出已知测试句，随后挂断 | 录音文件非空、能够播放、话单关联正确 |
| 实时转写 | 通话中说出测试句 | 真实 Provider 标识、增量和最终文本、WebSocket 消息与转写落库 |
| 非工作时间 | 在对应时段测试或使用单独的测试分支条件 | 命中关闭分支并挂机，保存流程版本和 `callId` |
| 识别故障 | 在隔离环境使测试引擎不可用 | ASR 记录异常，电话仍可继续和正常结束，录音按原配置保留 |

录音需要设置双方约定的提示音、FreeSWITCH 可写的录音目录和实际对象存储；本公开示例没有预置提示音和存储凭证。测试完成后记录应用源码校验值、前端快照、FreeSWITCH 版本、引擎及网络环境，再填写 [验收记录模板](acceptance.md)。

## 5. 当前验证状态

流程文件和脚本已提供。是否执行过真实呼叫、具体通过项及剩余限制，以 [本次验证记录](../../docs/open-source/verification.md) 为准。未填入实际证据的项目保持“待验证”。
