package com.och.system.util;

import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.enums.RecordStatusEnum;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 通话话单字段级“守卫式合并”纯逻辑（任务 9.8）。
 *
 * <p>将入参话单中尚未落库的字段合并到现有话单：仅当现有值为空、且新值非空时写入，
 * 录音状态终态时不允许回退。该类不访问数据库，便于单元测试；持久化由调用方完成。</p>
 *
 * <p>抽离为独立工具，避免并发 ESL 事件互相覆盖接听时间/结束时间/挂断原因等关键字段。</p>
 *
 * @author och
 */
public final class CallRecordMergeUtil {

    private CallRecordMergeUtil() {
    }

    /**
     * 将 incoming 中尚未落库的字段合并进 existing（就地修改），返回是否产生变更。
     *
     * @param existing 数据库现有话单（将被就地修改）
     * @param incoming 携带最新字段的话单
     * @return true=至少一个字段被填充
     */
    public static boolean merge(CallRecord existing, CallRecord incoming) {
        if (existing == null || incoming == null) {
            return false;
        }
        boolean[] changed = {false};
        boolean provisional = existing.getCallEndTime() == null;

        // 时间类字段：已设置则不覆盖
        pick(existing.getAnswerTime(), incoming.getAnswerTime(), existing::setAnswerTime, changed);
        pick(existing.getCallEndTime(), incoming.getCallEndTime(), existing::setCallEndTime, changed);
        pick(existing.getRingingTime(), incoming.getRingingTime(), existing::setRingingTime, changed);
        pick(existing.getCallStartTime(), incoming.getCallStartTime(), existing::setCallStartTime, changed);

        // 结果类字段：已设置则不覆盖（不回退）
        pick(existing.getHangupCauseCode(), incoming.getHangupCauseCode(), existing::setHangupCauseCode, changed);
        pick(existing.getHangupDir(), incoming.getHangupDir(), existing::setHangupDir, changed);
        pick(existing.getAnswerFlag(), incoming.getAnswerFlag(), existing::setAnswerFlag, changed);
        pick(existing.getCallState(), incoming.getCallState(), existing::setCallState, changed);

        // 录音字段：地址/时长已设置则不覆盖
        pick(existing.getRecordUrl(), incoming.getRecordUrl(), existing::setRecordUrl, changed);
        pick(existing.getFilePath(), incoming.getFilePath(), existing::setFilePath, changed);
        pick(existing.getRecordDuration(), incoming.getRecordDuration(), existing::setRecordDuration, changed);
        pick(existing.getRecordStartTime(), incoming.getRecordStartTime(), existing::setRecordStartTime, changed);
        pick(existing.getRecordEndTime(), incoming.getRecordEndTime(), existing::setRecordEndTime, changed);
        pick(existing.getSatisfaction(), incoming.getSatisfaction(), existing::setSatisfaction, changed);
        pick(existing.getSatisfactionTime(), incoming.getSatisfactionTime(), existing::setSatisfactionTime, changed);

        // 录音快照使用临时 callState/answerFlag，最终挂机事件带结束时间时允许一次性修正。
        if (provisional && incoming.getCallEndTime() != null) {
            replace(existing.getAnswerFlag(), incoming.getAnswerFlag(), existing::setAnswerFlag, changed);
            replace(existing.getCallState(), incoming.getCallState(), existing::setCallState, changed);
        }
        // 录音状态：允许向前推进，禁止回退；终态(已上传/上传失败)不可被覆盖
        if (incoming.getRecordStatus() != null) {
            RecordStatusEnum current = RecordStatusEnum.getByCode(existing.getRecordStatus());
            RecordStatusEnum target = RecordStatusEnum.getByCode(incoming.getRecordStatus());
            boolean allow;
            if (current == null) {
                allow = true; // 首次填充
            } else if (current.isTerminal()) {
                allow = false; // 终态不可回退
            } else {
                // 非终态：仅允许向前推进（含等值的幂等重放则不产生变更）
                allow = target != null && target.getCode() > current.getCode();
            }
            if (allow && !java.util.Objects.equals(existing.getRecordStatus(), incoming.getRecordStatus())) {
                existing.setRecordStatus(incoming.getRecordStatus());
                changed[0] = true;
            }
        }

        return changed[0];
    }

    /**
     * 仅当现有值为空、且新值非空时写入；命中则标记 changed。
     */
    private static <T> void pick(T existingValue, T incomingValue, Consumer<T> setter, boolean[] changed) {
        if (incomingValue != null && existingValue == null) {
            setter.accept(incomingValue);
            changed[0] = true;
        }
    }

    private static <T> void replace(T existingValue, T incomingValue, Consumer<T> setter, boolean[] changed) {
        if (incomingValue != null && !Objects.equals(existingValue, incomingValue)) {
            setter.accept(incomingValue);
            changed[0] = true;
        }
    }
}
