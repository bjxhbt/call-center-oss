package com.och.system.service;

import com.och.common.config.redis.RedisService;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.enums.BusinessCallStatusEnum;
import com.och.system.domain.enums.HangupDirectionEnum;
import com.och.system.util.CallRecordMergeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 挂断落库一致性验收（计划 06 · 任务 6「挂断录音话单」方案一部分）。
 *
 * <p>覆盖主叫挂机 / 被叫挂机 / 系统挂断三个方向，以及正常挂断与异常结束（失败/无应答），
 * 验证：话单的挂断方向、挂断原因、呼叫结果、结束时间、录音信息按方向正确落库；
 * 重复 HANGUP 与录音终态不被覆盖；通道释放后话单与录音一致。</p>
 *
 * @author och
 */
class CallHangupFinalizationTest {

    private RedisService redisService;
    private CallEventIdempotentService idempotent;
    private final Set<String> takenKeys = new HashSet<>();

    @BeforeEach
    void setUp() {
        redisService = Mockito.mock(RedisService.class);
        takenKeys.clear();
        when(redisService.tryLock(anyString(), anyString(), anyLong()))
                .thenAnswer(inv -> takenKeys.add(inv.getArgument(0)));
        idempotent = new CallEventIdempotentService(redisService);
    }

    /**
     * 模拟方案三在 HANGUP 事件时的落库：幂等闸门 + 守卫合并。
     * 返回是否实际写入。
     */
    private boolean finalizeHangup(CallRecord db, String callId, int hangupDir,
                                   Integer hangupCause, Integer callState,
                                   String recordUrl, Integer recordDuration, Integer recordStatus) {
        if (!idempotent.tryAcquire(callId, "HANGUP")) {
            return false; // 重复挂断事件
        }
        CallRecord incoming = new CallRecord();
        incoming.setHangupDir(hangupDir);
        incoming.setHangupCauseCode(hangupCause);
        incoming.setCallState(callState);
        incoming.setCallEndTime(new Date());
        incoming.setRecordUrl(recordUrl);
        incoming.setRecordDuration(recordDuration);
        incoming.setRecordStatus(recordStatus);
        return CallRecordMergeUtil.merge(db, incoming);
    }

    @Test
    void callerHangup_normalClearing_marksSuccess() {
        CallRecord db = new CallRecord();
        boolean changed = finalizeHangup(db, "CALL-H1",
                HangupDirectionEnum.CALLER.getCode(), 16, 1,
                "http://oss/h1.wav", 60, 2);
        assertThat(changed).isTrue();
        assertThat(db.getHangupDir()).isEqualTo(HangupDirectionEnum.CALLER.getCode());
        assertThat(db.getCallState()).isEqualTo(1); // 成功
        assertThat(db.getCallEndTime()).isNotNull();
        assertThat(db.getRecordUrl()).isEqualTo("http://oss/h1.wav");
        assertThat(db.getRecordStatus()).isEqualTo(2); // 已上传
    }

    @Test
    void calleeHangup_normalClearing_marksSuccess() {
        CallRecord db = new CallRecord();
        boolean changed = finalizeHangup(db, "CALL-H2",
                HangupDirectionEnum.CALLEE.getCode(), 16, 1, null, null, null);
        assertThat(changed).isTrue();
        assertThat(db.getHangupDir()).isEqualTo(HangupDirectionEnum.CALLEE.getCode());
        assertThat(db.getCallState()).isEqualTo(1);
    }

    @Test
    void systemHangup_noAnswer_marksFailed() {
        // 系统挂机 + 无人接听 → 失败
        CallRecord db = new CallRecord();
        boolean changed = finalizeHangup(db, "CALL-H3",
                HangupDirectionEnum.SYSTEM.getCode(), 19, 2, null, null, null);
        assertThat(changed).isTrue();
        assertThat(db.getHangupDir()).isEqualTo(HangupDirectionEnum.SYSTEM.getCode());
        assertThat(db.getCallState()).isEqualTo(2); // 失败
    }

    @Test
    void duplicateHangup_isDeduplicated() {
        CallRecord db = new CallRecord();
        assertThat(finalizeHangup(db, "CALL-H4", HangupDirectionEnum.CALLER.getCode(), 16, 1,
                "http://oss/h4.wav", 60, 2)).isTrue();
        // 重复挂断：幂等拦截，且不覆盖已有结果
        boolean again = finalizeHangup(db, "CALL-H4", HangupDirectionEnum.SYSTEM.getCode(), 19, 2,
                "http://oss/h4-dup.wav", 999, 3);
        assertThat(again).isFalse();
        assertThat(db.getHangupDir()).isEqualTo(HangupDirectionEnum.CALLER.getCode());
        assertThat(db.getCallState()).isEqualTo(1);
        assertThat(db.getRecordUrl()).isEqualTo("http://oss/h4.wav"); // 录音不被覆盖
    }

    @Test
    void recordingUploadAfterHangup_mergesWithoutOverwritingTerminalCallState() {
        // 挂断后录音上传到达：补充录音字段，但不改写已落库的呼叫结果/结束时间
        CallRecord db = new CallRecord();
        finalizeHangup(db, "CALL-H5", HangupDirectionEnum.CALLER.getCode(), 16, 1, null, null, null);
        Date endTime = db.getCallEndTime();

        CallRecord rec = new CallRecord();
        rec.setRecordUrl("http://oss/h5.wav");
        rec.setRecordDuration(120);
        rec.setRecordStatus(2);
        boolean merged = CallRecordMergeUtil.merge(db, rec);

        assertThat(merged).isTrue();
        assertThat(db.getRecordUrl()).isEqualTo("http://oss/h5.wav");
        assertThat(db.getRecordDuration()).isEqualTo(120);
        assertThat(db.getRecordStatus()).isEqualTo(2);
        // 挂断关键字段未被录音事件覆盖
        assertThat(db.getCallState()).isEqualTo(1);
        assertThat(db.getCallEndTime()).isEqualTo(endTime);
        assertThat(db.getHangupDir()).isEqualTo(HangupDirectionEnum.CALLER.getCode());
    }

    @Test
    void abnormalEnd_doesNotOverwriteTerminalStatus() {
        // 异常结束(FAILED)为终态；迟到的“成功”或更低级状态不应回退
        CallRecord db = new CallRecord();
        // 业务状态机层：FAILED 终态后不能再推进到 ANSWERED
        assertThat(BusinessCallStatusEnum.FAILED.canTransitTo(BusinessCallStatusEnum.ANSWERED)).isFalse();
        assertThat(BusinessCallStatusEnum.NO_ANSWER.canTransitTo(BusinessCallStatusEnum.HANGUP)).isFalse();
        // 话单层：callState 已为失败，重复/迟到事件不覆盖
        db.setCallState(2);
        CallRecord late = new CallRecord();
        late.setCallState(1); // 试图改写为成功
        assertThat(CallRecordMergeUtil.merge(db, late)).isFalse();
        assertThat(db.getCallState()).isEqualTo(2);
    }
}
