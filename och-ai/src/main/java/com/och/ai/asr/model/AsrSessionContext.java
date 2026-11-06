package com.och.ai.asr.model;

import com.och.ai.asr.AsrErrorCode;
import lombok.Data;

/**
 * 运行期 ASR 会话上下文（方案 7.5 / 7.15）。
 *
 * <p>维护云端 sessionId 与业务 callId/UUID 的关系、会话状态、重连计数与去重游标，
 * 用于：幂等创建、增量覆盖去重、有限重连防文本重复、最终结果等待。</p>
 *
 * <p>非持久化对象，仅存在于内存/缓存；最终文本由 {@code CallTranscriptSegment} 持久化。</p>
 */
@Data
public class AsrSessionContext {

    /** 业务侧会话 ID（一般以 callId 派生，幂等键）。 */
    private String sessionId;

    /** 云端会话 ID（鉴权后由 Provider 回填）。 */
    private String cloudSessionId;

    /** 业务呼叫 ID。 */
    private Long callId;

    /** 当前通道 UUID。 */
    private String uniqueId;

    /** 对端通道 UUID。 */
    private String otherUniqueId;

    /** 主叫号码。 */
    private String caller;

    /** 被叫号码。 */
    private String callee;

    /** 坐席 ID（WebSocket 推送定位用）。 */
    private Long agentId;

    /** 坐席号码。 */
    private String agentNumber;

    /** 坐席名称。 */
    private String agentName;

    /** 呼叫方向：1-呼出 2-呼入。 */
    private Integer direction;

    /** 实际生效 Provider（可能因缺凭证降级为 MOCK）。 */
    private AsrProviderType provider;

    /** 目标采样率。 */
    private int sampleRate;

    /** 入口编码。 */
    private AudioCodec codec;

    /** 会话状态。 */
    private AsrSessionState state;

    /** 创建时间（毫秒）。 */
    private long createdAt;

    /** 首帧音频发送时间（毫秒）。 */
    private long firstAudioSentAt;

    /** 首条文本到达时间（毫秒）。 */
    private long firstTextAt;

    /** 最近一次最终结果的 segmentId（重连去重游标）。 */
    private String lastFinalSegmentId;

    /** 最近一次最终结果的 sequence（重连去重游标）。 */
    private int lastFinalSequence;

    /** 已重连次数。 */
    private int reconnectCount;

    /** 已发送音频帧数。 */
    private long audioFramesSent;

    /** 已接收音频帧数（含静音/重复，连续性统计用）。 */
    private long framesReceived;

    /** 检测到的丢帧（序号跳变）次数。 */
    private long gapsDetected;

    /** 检测到的重复/乱序帧次数。 */
    private long duplicateOrOutOfOrder;

    /** 最近一帧序号（连续性判定基准，-1 表示尚未建立基准）。 */
    private long lastFrameSequence = -1L;

    /** 桥接时间（毫秒）。 */
    private Long bridgeTime;

    /** 录音文件路径（异常时用于离线补偿）。 */
    private String record;

    /** 转写摘要主键（getOrCreateSummary 后回填，便于段落落库关联）。 */
    private Long transcriptId;

    /** 最近一次错误码（AsrErrorCode）。 */
    private String lastErrorCode;

    public AsrSessionContext() {
        this.state = AsrSessionState.CREATED;
        this.createdAt = System.currentTimeMillis();
    }

    /** 标记错误状态并记录错误码。 */
    public void markError(String code) {
        this.state = AsrSessionState.ERROR;
        this.lastErrorCode = code == null ? AsrErrorCode.UNKNOWN : code;
    }
}
