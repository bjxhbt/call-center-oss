
package com.och.esl.handler.esl;

import com.alibaba.fastjson.JSONObject;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.EslEventNames;
import com.och.common.domain.CallInfo;
import com.och.common.utils.StringUtils;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.utils.EslEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.stereotype.Component;

/**
 * 开始录音
 * @author danmo
 * @date 2023年09月18日 19:03
 */
@Slf4j
@EslEventName(EslEventNames.RECORD_START)
@Component
public class FsChannelRecordStartEslEventHandler extends AbstractFsEslEventHandler {

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        log.info("LfsChannelRecordStartEslEventHandler EslEvent:{}.", JSONObject.toJSONString(event));
        String uniqueId = EslEventUtil.getUniqueId(event);
        CallInfo callInfo = resolveCallInfo(event);
        if (callInfo == null) {
            return;
        }
        long recordStartTime = event.getEventDateTimestamp() > 0
                ? event.getEventDateTimestamp() / 1000
                : System.currentTimeMillis();
        callInfo.setRecordStartTime(recordStartTime);
        String eventRecordPath = EslEventUtil.getRecordFilePath(event);
        if (StringUtils.isNotBlank(eventRecordPath)) {
            callInfo.setRecord(eventRecordPath.trim());
        }
        try {
            iCallRecordService.ensureRecordingRecord(callInfo);
        } catch (Exception exception) {
            log.error("录音开始事件补偿建单失败 callId:{} uniqueId:{}",
                    callInfo.getCallId(), uniqueId, exception);
        }
        ifsCallCacheService.saveCallInfo(callInfo);
    }
}
