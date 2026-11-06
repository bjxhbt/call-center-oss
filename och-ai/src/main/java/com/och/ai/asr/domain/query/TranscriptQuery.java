package com.och.ai.asr.domain.query;

import lombok.Data;

/**
 * 转写摘要分页查询条件（方案 7.14）：按 callId、号码、坐席、方向、状态过滤。
 */
@Data
public class TranscriptQuery {

    /** 呼叫唯一ID。 */
    private String callId;
    /** 主叫号码。 */
    private String callerNumber;
    /** 被叫号码。 */
    private String calleeNumber;
    /** 坐席ID。 */
    private Long agentId;
    /** 坐席号码。 */
    private String agentNumber;
    /** 呼叫方向 1-呼出 2-呼入。 */
    private Integer direction;
    /** 转写状态 RUNNING/SUCCESS/PARTIAL/FAILED/OFFLINE。 */
    private String status;
    /** ASR 服务商。 */
    private String provider;

    /** 页码（从 1 开始）。 */
    private Integer pageNum = 1;
    /** 每页大小。 */
    private Integer pageSize = 20;
}
