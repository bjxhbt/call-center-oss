package com.och.system.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一数据字典枚举单元测试（任务 1.3 / 9.8）。
 *
 * @author och
 */
class DataDictionaryEnumTest {

    @Test
    void agentOnlineStatus_roundTrip() {
        assertThat(AgentOnlineStatusEnum.getByCode(1)).isEqualTo(AgentOnlineStatusEnum.IDLE);
        assertThat(AgentOnlineStatusEnum.getByCode(5)).isEqualTo(AgentOnlineStatusEnum.TALKING);
        assertThat(AgentOnlineStatusEnum.getByCode(99)).isNull();
        assertThat(AgentOnlineStatusEnum.getByCode(null)).isNull();
        assertThat(AgentOnlineStatusEnum.IDLE.getCode()).isEqualTo(1);
        assertThat(AgentOnlineStatusEnum.TALKING.getDesc()).isEqualTo("通话中");
    }

    @Test
    void recordStatus_terminalGuard() {
        assertThat(RecordStatusEnum.UPLOADED.isTerminal()).isTrue();
        assertThat(RecordStatusEnum.UPLOAD_FAILED.isTerminal()).isTrue();
        assertThat(RecordStatusEnum.NOT_UPLOADED.isTerminal()).isFalse();
        assertThat(RecordStatusEnum.UPLOADING.isTerminal()).isFalse();
    }

    @Test
    void transcribeStatus_terminalGuard() {
        assertThat(TranscribeStatusEnum.COMPLETED.isTerminal()).isTrue();
        assertThat(TranscribeStatusEnum.FAILED.isTerminal()).isTrue();
        assertThat(TranscribeStatusEnum.SKIPPED.isTerminal()).isTrue();
        assertThat(TranscribeStatusEnum.TRANSCRIBING.isTerminal()).isFalse();
        assertThat(TranscribeStatusEnum.PENDING.isTerminal()).isFalse();
    }

    @Test
    void businessCallStatus_canTransitTo_forwardOnly() {
        // 同状态允许（幂等重放）
        assertThat(BusinessCallStatusEnum.RINGING.canTransitTo(BusinessCallStatusEnum.RINGING)).isTrue();
        // 向后推进允许
        assertThat(BusinessCallStatusEnum.INITIATING.canTransitTo(BusinessCallStatusEnum.RINGING)).isTrue();
        assertThat(BusinessCallStatusEnum.RINGING.canTransitTo(BusinessCallStatusEnum.ANSWERED)).isTrue();
        assertThat(BusinessCallStatusEnum.ANSWERED.canTransitTo(BusinessCallStatusEnum.HANGUP)).isTrue();
        // 逆向回退禁止
        assertThat(BusinessCallStatusEnum.ANSWERED.canTransitTo(BusinessCallStatusEnum.RINGING)).isFalse();
        assertThat(BusinessCallStatusEnum.RINGING.canTransitTo(BusinessCallStatusEnum.INITIATING)).isFalse();
        // 终态只允许相同终态，禁止回退到非终态
        assertThat(BusinessCallStatusEnum.HANGUP.canTransitTo(BusinessCallStatusEnum.HANGUP)).isTrue();
        assertThat(BusinessCallStatusEnum.HANGUP.canTransitTo(BusinessCallStatusEnum.ANSWERED)).isFalse();
        assertThat(BusinessCallStatusEnum.HANGUP.canTransitTo(BusinessCallStatusEnum.RINGING)).isFalse();
        // null 拒绝
        assertThat(BusinessCallStatusEnum.RINGING.canTransitTo(null)).isFalse();
    }

    @Test
    void hangupDirection_andAnswerFlag_lookup() {
        assertThat(HangupDirectionEnum.getByCode(1)).isEqualTo(HangupDirectionEnum.CALLER);
        assertThat(HangupDirectionEnum.getByCode(3)).isEqualTo(HangupDirectionEnum.SYSTEM);
        assertThat(AnswerFlagEnum.getByCode(0)).isEqualTo(AnswerFlagEnum.BOTH_ANSWERED);
        assertThat(AnswerFlagEnum.getByCode(2)).isEqualTo(AnswerFlagEnum.AGENT_ANSWERED_ONLY);
    }
}
