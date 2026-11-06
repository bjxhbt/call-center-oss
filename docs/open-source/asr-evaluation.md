# 真实语音评估

工具：[evaluate_asr.py](../../tools/open-source/evaluate_asr.py)。运行要求：Python 3.9 或以上，仅使用标准库。结果表：[asr-results.csv](../../examples/public-demo/asr-results.csv)。空白表不包含任何实测数据。

## 音频来源与环境

测试音频分为 `synthetic`（合成语音）、`recorded`（直接录制）和 `real_phone`（实际电话链路采样），Mock 输出必须标记为 `mock`。不同来源和不同 Provider 分组统计，不能把 Mock 指标或高清录音结果当成电话链路效果。

每份音频在 [测试集清单](../../examples/public-demo/asr-testset.csv) 中记录参考文本、关键词、音频 SHA-256、采样率、编码及使用许可。公开音频应由维护者录制或来自明确允许再分发的数据集；未确认许可的素材只在本地使用。当前没有将现场语音资料或模型文件加入公开包。

建议覆盖普通话、数字号码、日期金额、业务词和噪声场景。记录应用版本、模型版本、网络、硬件和并发设置，同一比较使用相同音频、参考文本与标准化规则。

## 结果字段与指标口径

| 字段 | 填写要求 |
| --- | --- |
| `file` | 与测试集清单对应的文件标识 |
| `provider` | 实际生效的引擎，例如 TENCENT、ALIYUN、FUNASR 或 MOCK |
| `source` | synthetic、recorded、real_phone 或 mock |
| `reference_text` | 人工核对的参考文本，必须非空 |
| `hypothesis_text` | 引擎最终识别文本，失败时允许为空 |
| `keywords` | 需要识别的关键词，以 `|` 分隔 |
| `first_text_latency_ms` | 第一帧音频发送至首次文本到达的毫秒数 |
| `final_text_latency_ms` | 当前句最后一帧音频发送至最终文本到达的毫秒数 |
| `result` | success、partial 或 failed |

不确定的延迟留空，不能填 0 代表未知。现有数据库中的 `finalLatencyMs` 与历史报告涉及不同计时起点，未核对实际采集逻辑前不要直接填入本表的最终文本延迟。

脚本统一执行 NFKC 字符标准化、英文大小写归一化，并移除空白与标点。CER 使用“所有样本的编辑距离之和 / 参考字符数之和”，失败样本按全文漏识别计入，避免只统计成功请求。每条样本中的关键词标准化、去重并移除空项，命中率按全部有效关键词计算。延迟输出有有效测量的样本数、中位数和最近秩 P95，并分别报告成功、部分完成和失败请求数。

```bash
python tools/open-source/evaluate_asr.py examples/public-demo/asr-results.csv --output outputs/asr-evaluation/report.json
```

空表、重复样本、将 MOCK 标为真实来源、负数或非有限延迟会报错，不会生成伪造的效果报告。

## 真实服务执行

先按 [通话示例](../../examples/public-demo/README.md) 接通一家真实引擎，保存音频与最终文本。需要单独验证文件识别时，可以使用已有模拟入口投递真实 WAV，但应将其标记为文件联调，不能据此认定 FreeSWITCH 的实时音频已经接通。

每组同时统计请求失败和异常恢复情况。通话稳定性至少保存“识别故障后仍可通话、录音按配置保留、会话最终释放、错误可查”的证据。当前接口测试通过与识别效果评估是不同的验证事项。

本次未提供真实云凭证或电话采样结果，所以不填写准确率、延迟和并发承诺。完成真实采样后再以报告中的实测结果描述能力。
