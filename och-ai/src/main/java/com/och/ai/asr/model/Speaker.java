package com.och.ai.asr.model;

/**
 * 说话人标识（方案 7.10 / 7.11）。
 *
 * <p>与前端 {@code TranscriptSegment.speaker}（consts/softphone.ts）对齐：
 * {@code customer}/{@code agent}/{@code unknown}。</p>
 */
public enum Speaker {

    CUSTOMER("customer"),
    AGENT("agent"),
    UNKNOWN("unknown");

    private final String code;

    Speaker(String code) {
        this.code = code;
    }

    /** 前端契约字符串。 */
    public String code() {
        return code;
    }

    /**
     * 容错解析：兼容常见输入（customer/callee/client/0/left → CUSTOMER；
     * agent/caller/1/right → AGENT；其余 → UNKNOWN）。
     */
    public static Speaker parse(Object value) {
        if (value == null) {
            return UNKNOWN;
        }
        String v = String.valueOf(value).trim().toLowerCase();
        return switch (v) {
            case "customer", "callee", "client", "0", "left", "被叫", "客户" -> CUSTOMER;
            case "agent", "caller", "1", "right", "坐席", "主叫" -> AGENT;
            default -> UNKNOWN;
        };
    }
}
