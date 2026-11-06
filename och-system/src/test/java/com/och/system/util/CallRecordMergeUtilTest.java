package com.och.system.util;

import com.och.common.domain.CallInfo;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.enums.RecordStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通话话单守卫式合并逻辑单元测试（任务 9.8）。
 * 验证并发事件不会覆盖已落库的接听/结束时间与挂断原因。
 *
 * @author och
 */
class CallRecordMergeUtilTest {

    @Test
    void merge_fillsAbsentFields() {
        CallRecord existing = new CallRecord();
        CallRecord incoming = new CallRecord();
        Date answer = new Date(1_700_000_000_000L);
        incoming.setAnswerTime(answer);
        incoming.setHangupCauseCode(16);
        incoming.setCallState(1);

        boolean changed = CallRecordMergeUtil.merge(existing, incoming);

        assertThat(changed).isTrue();
        assertThat(existing.getAnswerTime()).isEqualTo(answer);
        assertThat(existing.getHangupCauseCode()).isEqualTo(16);
        assertThat(existing.getCallState()).isEqualTo(1);
    }

    @Test
    void merge_doesNotOverwritePresentFields() {
        // 模拟并发：ANSWER 事件重复下发，原有应答时间不能被覆盖
        Date firstAnswer = new Date(1_700_000_000_000L);
        CallRecord existing = new CallRecord();
        existing.setAnswerTime(firstAnswer);
        existing.setCallEndTime(new Date(1_700_000_500_000L));
        existing.setHangupCauseCode(16);

        CallRecord incoming = new CallRecord();
        // 迟到的并发事件携带更晚的时间与不同挂断原因
        incoming.setAnswerTime(new Date(1_700_000_900_000L));
        incoming.setCallEndTime(new Date(1_700_001_000_000L));
        incoming.setHangupCauseCode(17);

        boolean changed = CallRecordMergeUtil.merge(existing, incoming);

        // 时间/结果字段都已存在，不应变更
        assertThat(changed).isFalse();
        assertThat(existing.getAnswerTime()).isEqualTo(firstAnswer);
        assertThat(existing.getHangupCauseCode()).isEqualTo(16);
    }

    @Test
    void merge_recordStatusTerminalDoesNotRegress() {
        // 录音已上传(终态)，迟到的“上传中”事件不应回退
        CallRecord existing = new CallRecord();
        existing.setRecordStatus(RecordStatusEnum.UPLOADED.getCode());

        CallRecord incoming = new CallRecord();
        incoming.setRecordStatus(RecordStatusEnum.UPLOADING.getCode());

        boolean changed = CallRecordMergeUtil.merge(existing, incoming);

        assertThat(changed).isFalse();
        assertThat(existing.getRecordStatus()).isEqualTo(RecordStatusEnum.UPLOADED.getCode());
    }

    @Test
    void merge_recordStatusCanAdvanceFromNonTerminal() {
        CallRecord existing = new CallRecord();
        existing.setRecordStatus(RecordStatusEnum.UPLOADING.getCode());

        CallRecord incoming = new CallRecord();
        incoming.setRecordStatus(RecordStatusEnum.UPLOADED.getCode());

        boolean changed = CallRecordMergeUtil.merge(existing, incoming);

        assertThat(changed).isTrue();
        assertThat(existing.getRecordStatus()).isEqualTo(RecordStatusEnum.UPLOADED.getCode());
    }

    @Test
    void merge_fillsSatisfactionFieldsWithoutOverwritingExistingValue() {
        Date satisfactionTime = new Date(1_700_000_000_000L);
        CallRecord existing = new CallRecord();
        CallRecord incoming = new CallRecord();
        incoming.setSatisfaction("5");
        incoming.setSatisfactionTime(satisfactionTime);

        assertThat(CallRecordMergeUtil.merge(existing, incoming)).isTrue();
        assertThat(existing.getSatisfaction()).isEqualTo("5");
        assertThat(existing.getSatisfactionTime()).isEqualTo(satisfactionTime);

        CallRecord late = new CallRecord();
        late.setSatisfaction("1");
        late.setSatisfactionTime(new Date(satisfactionTime.getTime() + 1_000));
        assertThat(CallRecordMergeUtil.merge(existing, late)).isFalse();
        assertThat(existing.getSatisfaction()).isEqualTo("5");
        assertThat(existing.getSatisfactionTime()).isEqualTo(satisfactionTime);
    }

    @Test
    void merge_finalHangupCompletesProvisionalRecordingSnapshot() {
        CallRecord existing = new CallRecord();
        existing.setCallState(2);
        existing.setAnswerFlag(0);
        existing.setRecordStatus(0);

        CallRecord incoming = new CallRecord();
        incoming.setCallEndTime(new Date(1_700_000_100_000L));
        incoming.setCallState(1);
        incoming.setAnswerFlag(0);
        incoming.setHangupCauseCode(16);
        incoming.setRecordEndTime(new Date(1_700_000_099_000L));

        assertThat(CallRecordMergeUtil.merge(existing, incoming)).isTrue();
        assertThat(existing.getCallState()).isEqualTo(1);
        assertThat(existing.getCallEndTime()).isEqualTo(incoming.getCallEndTime());
        assertThat(existing.getRecordEndTime()).isEqualTo(incoming.getRecordEndTime());
    }

    @Test
    void transferForRecordingCreatesVisibleProvisionalRecord() {
        CallInfo callInfo = CallInfo.builder()
                .callId(3L)
                .callTime(1_700_000_000_000L)
                .answerTime(1_700_000_001_000L)
                .direction(1)
                .record("/usr/local/freeswitch/recordings/2026-08-20/3.wav")
                .recordStartTime(1_700_000_001_000L)
                .build();

        CallRecord record = new CallRecord();
        record.transferForRecording(callInfo);

        assertThat(record.getCallId()).isEqualTo("3");
        assertThat(record.getFilePath()).endsWith("3.wav");
        assertThat(record.getRecordStatus()).isZero();
        assertThat(record.getCallState()).isEqualTo(2);
        assertThat(record.getCallEndTime()).isNull();
    }

    @Test
    void merge_nullArgsReturnFalse() {
        assertThat(CallRecordMergeUtil.merge(null, new CallRecord())).isFalse();
        assertThat(CallRecordMergeUtil.merge(new CallRecord(), null)).isFalse();
    }

    @Test
    void transfer_marksUnansweredCallAsUnanswered() {
        CallInfo callInfo = CallInfo.builder()
                .callId(1L)
                .callTime(1_700_000_000_000L)
                .direction(1)
                .build();

        CallRecord record = new CallRecord();
        record.transfer(callInfo);

        assertThat(record.getAnswerFlag()).isEqualTo(1);
        assertThat(record.getAnswerTime()).isNull();
    }

    @Test
    void transfer_marksAnsweredCallAsAnswered() {
        Date answer = new Date(1_700_000_001_000L);
        CallInfo callInfo = CallInfo.builder()
                .callId(2L)
                .callTime(1_700_000_000_000L)
                .answerTime(answer.getTime())
                .direction(1)
                .build();

        CallRecord record = new CallRecord();
        record.transfer(callInfo);

        assertThat(record.getAnswerFlag()).isEqualTo(0);
        assertThat(record.getAnswerTime()).isEqualTo(answer);
    }
}
