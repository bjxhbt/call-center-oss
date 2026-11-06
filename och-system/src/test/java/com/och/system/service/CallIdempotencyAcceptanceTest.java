package com.och.system.service;

import com.och.common.config.redis.RedisService;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.enums.BusinessCallStatusEnum;
import com.och.system.domain.enums.RecordStatusEnum;
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
 * 幂等与状态校验「回放」验收测试（计划 9.2 P1 / 任务 9.8）。
 *
 * <p>模拟方案三 ESL 事件处理链：幂等键去重 -&gt; 状态机校验 -&gt; 守卫式合并，覆盖：</p>
 * <ul>
 *   <li>重复 ANSWER / HANGUP 投递：不重复处理、不覆盖已落库字段；</li>
 *   <li>乱序投递（HANGUP 后迟到 ANSWER/BRIDGE）：终态不被非终态回退覆盖；</li>
 *   <li>重复录音/转写终态：不重复生成、不回退。</li>
 * </ul>
 * 不依赖真实数据库/Redis，用 Mockito 模拟 tryLock 的 SET NX EX 语义。
 *
 * @author och
 */
class CallIdempotencyAcceptanceTest {

    private RedisService redisService;
    private CallEventIdempotentService idempotent;

    /** 模拟 Redis SET NX EX：同一 key 首次返回 true，之后返回 false。 */
    private final Set<String> takenKeys = new HashSet<>();

    /** 模拟单条话单“数据库”行（同一 callId）。 */
    private CallRecord dbRecord;

    /** 模拟业务呼叫生命周期状态（ESL 层维护，非 CallRecord 字段）。 */
    private BusinessCallStatusEnum currentStatus;

    @BeforeEach
    void setUp() {
        redisService = Mockito.mock(RedisService.class);
        takenKeys.clear();
        when(redisService.tryLock(anyString(), anyString(), anyLong()))
                .thenAnswer(inv -> takenKeys.add(inv.getArgument(0)));
        idempotent = new CallEventIdempotentService(redisService);
        dbRecord = null;
        currentStatus = null;
    }

    /**
     * 模拟一次 ESL 事件处理：幂等闸门 -&gt; 状态机 -&gt; 守卫合并。
     *
     * @return true=实际处理；false=被幂等/状态守卫拦截
     */
    private boolean process(String callId, String eventType, BusinessCallStatusEnum target, CallRecord incoming) {
        // 1) 幂等键：同 (callId,eventType) 只处理一次
        if (!idempotent.tryAcquire(callId, eventType)) {
            return false;
        }
        // 2) 状态机：禁止终态回退到非终态
        if (currentStatus != null && !currentStatus.canTransitTo(target)) {
            return false;
        }
        // 3) 守卫式合并（新增 or 字段级更新）
        if (dbRecord == null) {
            dbRecord = new CallRecord();
        }
        CallRecordMergeUtil.merge(dbRecord, incoming);
        if (target != null) {
            currentStatus = target;
        }
        return true;
    }

    @Test
    void duplicateAnswer_isDeduplicated_answerTimeNotOverwritten() {
        String callId = "CALL-A";
        Date firstAnswer = new Date(1_700_000_000_000L);

        // 第一次 ANSWER：处理成功，落库应答时间
        CallRecord a1 = new CallRecord();
        a1.setAnswerTime(firstAnswer);
        assertThat(process(callId, "ANSWER", BusinessCallStatusEnum.ANSWERED, a1)).isTrue();
        assertThat(dbRecord.getAnswerTime()).isEqualTo(firstAnswer);

        // 重复 ANSWER：被幂等键拦截，不应处理
        CallRecord a2 = new CallRecord();
        a2.setAnswerTime(new Date(1_700_000_999_999L)); // 迟到事件携带更晚时间
        assertThat(process(callId, "ANSWER", BusinessCallStatusEnum.ANSWERED, a2)).isFalse();
        // 应答时间未被覆盖
        assertThat(dbRecord.getAnswerTime()).isEqualTo(firstAnswer);
    }

    @Test
    void hangup_thenLateAnswer_terminalNotRegressed() {
        String callId = "CALL-B";
        Date endTime = new Date(1_700_000_500_000L);

        // 先 HANGUP（终态）
        CallRecord hangup = new CallRecord();
        hangup.setCallEndTime(endTime);
        hangup.setCallState(1);
        assertThat(process(callId, "HANGUP", BusinessCallStatusEnum.HANGUP, hangup)).isTrue();
        assertThat(currentStatus).isEqualTo(BusinessCallStatusEnum.HANGUP);

        // 迟到的 ANSWER（不同 eventType，幂等键不同 -&gt; 幂等放行），但状态机禁止 HANGUP-&gt;ANSWERED
        CallRecord lateAnswer = new CallRecord();
        lateAnswer.setAnswerTime(new Date(1_700_000_800_000L));
        assertThat(process(callId, "ANSWER", BusinessCallStatusEnum.ANSWERED, lateAnswer)).isFalse();
        // 终态数据未被回退覆盖
        assertThat(dbRecord.getCallEndTime()).isEqualTo(endTime);
        assertThat(dbRecord.getAnswerTime()).isNull();
        assertThat(currentStatus).isEqualTo(BusinessCallStatusEnum.HANGUP);
    }

    @Test
    void duplicateHangup_isDeduplicated_noDoubleTerminal() {
        String callId = "CALL-C";

        CallRecord h1 = new CallRecord();
        h1.setCallEndTime(new Date(1_700_000_500_000L));
        h1.setCallState(1);
        assertThat(process(callId, "HANGUP", BusinessCallStatusEnum.HANGUP, h1)).isTrue();

        // 重复 HANGUP：幂等拦截
        CallRecord h2 = new CallRecord();
        h2.setCallEndTime(new Date(1_700_001_000_000L));
        h2.setCallState(2); // 试图改写结果
        assertThat(process(callId, "HANGUP", BusinessCallStatusEnum.HANGUP, h2)).isFalse();

        // 结果保持首次值，不被覆盖
        assertThat(dbRecord.getCallState()).isEqualTo(1);
        assertThat(dbRecord.getCallEndTime()).isEqualTo(new Date(1_700_000_500_000L));
    }

    @Test
    void bridge_afterHangup_blockedByStateMachine() {
        String callId = "CALL-D";
        // HANGUP 先到
        assertThat(process(callId, "HANGUP", BusinessCallStatusEnum.HANGUP, new CallRecord())).isTrue();
        // 迟到 BRIDGE：状态机禁止 HANGUP-&gt;ANSWERED
        assertThat(process(callId, "BRIDGE", BusinessCallStatusEnum.ANSWERED, new CallRecord())).isFalse();
        assertThat(currentStatus).isEqualTo(BusinessCallStatusEnum.HANGUP);
    }

    @Test
    void duplicateRecordUpload_terminalNotRegressed() {
        // 录音上传：先到 UPLOADED(终态)，迟到 UPLOADING 不应回退
        String callId = "CALL-E";
        CallRecord park = new CallRecord();
        assertThat(process(callId, "PARK", BusinessCallStatusEnum.RINGING, park)).isTrue();

        // 录音上传完成
        CallRecord up = new CallRecord();
        up.setRecordStatus(RecordStatusEnum.UPLOADED.getCode());
        up.setRecordUrl("http://oss/1.wav");
        CallRecordMergeUtil.merge(dbRecord, up);
        assertThat(dbRecord.getRecordStatus()).isEqualTo(RecordStatusEnum.UPLOADED.getCode());

        // 迟到的“上传中”不应回退终态
        CallRecord uploading = new CallRecord();
        uploading.setRecordStatus(RecordStatusEnum.UPLOADING.getCode());
        boolean changed = CallRecordMergeUtil.merge(dbRecord, uploading);
        assertThat(changed).isFalse();
        assertThat(dbRecord.getRecordStatus()).isEqualTo(RecordStatusEnum.UPLOADED.getCode());
    }
}
