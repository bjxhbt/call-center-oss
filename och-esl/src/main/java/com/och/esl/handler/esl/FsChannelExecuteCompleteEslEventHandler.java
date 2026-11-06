
package com.och.esl.handler.esl;


import cn.hutool.core.collection.CollectionUtil;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.EslEventNames;
import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.domain.CallInfoDetail;
import com.och.common.enums.RouteTypeEnum;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.CallRecordingService;
import com.och.esl.service.CallPlaybackCompletionService;
import com.och.esl.utils.EslEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.stereotype.Component;
import com.och.common.utils.StringUtils;
import com.alibaba.fastjson.JSONObject;

/**
 * 渠道对呼叫完成执行某些操作处理类
 *
 * @author danmo
 * @date 2023年09月18日 19:03
 */
@Slf4j
@EslEventName(EslEventNames.CHANNEL_EXECUTE_COMPLETE)
@Component
public class FsChannelExecuteCompleteEslEventHandler extends AbstractFsEslEventHandler {

    private final IFlowNoticeService iFlowNoticeService;
    private final CallRecordingService callRecordingService;
    private final CallPlaybackCompletionService callPlaybackCompletionService;

    public FsChannelExecuteCompleteEslEventHandler(IFlowNoticeService iFlowNoticeService,
                                                   CallRecordingService callRecordingService,
                                                   CallPlaybackCompletionService callPlaybackCompletionService) {
        this.iFlowNoticeService = iFlowNoticeService;
        this.callRecordingService = callRecordingService;
        this.callPlaybackCompletionService = callPlaybackCompletionService;
    }

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        log.info("ChannelExecuteCompleteEslEventHandler EslEvent:{}.", JSONObject.toJSONString(event));
        if (StringUtils.isBlank(EslEventUtil.getApplication(event))) {
            return;
        }
        String uniqueId = EslEventUtil.getUniqueId(event);
        CallInfo callInfo = resolveCallInfo(event);
        if (callInfo == null) {
            return;
        }
        switch (EslEventUtil.getApplication(event)) {
            case "playback":
                String playbackTerminator = event.getEventHeaders().get("variable_playback_terminator_used");
                if (StringUtils.isBlank(playbackTerminator)) {
                    String applicationResponse = EslEventUtil.getApplicationResponse(event);
                    if (StringUtils.length(applicationResponse) == 1 && "0123456789*#".contains(applicationResponse)) {
                        playbackTerminator = applicationResponse;
                    }
                }
                if (StringUtils.isNotBlank(playbackTerminator)) {
                    log.info("uniqueId:{}, playback interrupted by DTMF:{}", uniqueId, playbackTerminator);
                    callRecordingService.cancelAfterPlayback(callInfo.getCallId(), uniqueId);
                    if (callInfo.getFlowDataContext() != null && !com.och.common.enums.ProcessEnum.CALL_BRIDGE.equals(callInfo.getProcess())) {
                        iFlowNoticeService.notice(4, playbackTerminator, callInfo.getFlowDataContext());
                    }
                    break;
                }
                if ("FILE PLAYED".equals(EslEventUtil.getApplicationResponse(event))) {
                    log.info("uniqueId:{}, playback:{} success", uniqueId, EslEventUtil.getApplicationData(event));
                    if (callRecordingService.startAfterPlayback(address, callInfo.getCallId(), uniqueId)) {
                        log.info("uniqueId:{}, recording notice completed; IVR flow remains at current node", uniqueId);
                        break;
                    }
                    if (callPlaybackCompletionService.consumeHangupAfterPlayback(callInfo.getCallId(), uniqueId)) {
                        log.info("uniqueId:{}, busy prompt completed; hanging up callId:{}", uniqueId, callInfo.getCallId());
                        fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
                        break;
                    }
                    if (CollectionUtil.isNotEmpty(callInfo.getDetailList())){
                        CallInfoDetail callInfoDetail = callInfo.getDetailList().get(0);
                        if (callInfoDetail.getTransferType() == 2) {
                            if (callInfo.getFlowDataContext() != null && !com.och.common.enums.ProcessEnum.CALL_BRIDGE.equals(callInfo.getProcess())) {
                            iFlowNoticeService.notice(2,"next", callInfo.getFlowDataContext());
                        }
                        }
                    }else {
                        if (callInfo.getFlowDataContext() != null && !com.och.common.enums.ProcessEnum.CALL_BRIDGE.equals(callInfo.getProcess())) {
                            iFlowNoticeService.notice(2,"next", callInfo.getFlowDataContext());
                        }
                    }
/*                    if (callInfo.getQueueStartTime() != null && callInfo.getQueueEndTime() == null) {
                        fsClient.playFile(address, uniqueId, "queue.wav");
                        return;
                    }*/

                } else if ("FILE NOT FOUND".equals(EslEventUtil.getApplicationResponse(event))) {
                    // 降级:若本次播放是"录音提示音"(after-playback marker 存在),提示音缺失也不丢录音,
                    // 直接启动录音。线上曾因 OCH_RECORDING_NOTICE_PATH 丢失回退到 Windows 路径,
                    // 提示音找不到导致整通电话不录音。录音优先级高于提示音。
                    if (callRecordingService.startAfterPlayback(address, callInfo.getCallId(), uniqueId)) {
                        log.warn("录音提示音缺失,已降级直接启动录音 callId:{} uniqueId:{}",
                                callInfo.getCallId(), uniqueId);
                        break;
                    }
                    callRecordingService.cancelAfterPlayback(callInfo.getCallId(), uniqueId);
                    String fallbackPath = callPlaybackCompletionService.consumeFallbackAfterPlaybackFailure(
                            callInfo.getCallId(), uniqueId);
                    if (StringUtils.isNotBlank(fallbackPath)) {
                        log.error("uniqueId:{} file:{} not found, playing fallback:{}",
                                uniqueId, EslEventUtil.getApplicationData(event), fallbackPath);
                        fsClient.playFile(address, uniqueId, fallbackPath);
                        return;
                    }
                    callPlaybackCompletionService.consumeHangupAfterPlayback(callInfo.getCallId(), uniqueId);
                    log.error("uniqueId:{}  file:{} not found", uniqueId, EslEventUtil.getApplicationData(event));
                    fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
                    return;
                }
                break;

            case "play_and_get_digits":
                String menuDtmfReturn = EslEventUtil.getMenuDtmfReturn(event);
                iFlowNoticeService.notice(4,menuDtmfReturn, callInfo.getFlowDataContext());
                break;
            case "break":
                break;

            default:
                break;
        }
        log.debug("execute:{}, data:{}, response:{}", EslEventUtil.getApplication(event), EslEventUtil.getApplicationData(event), EslEventUtil.getApplicationResponse(event));
    }
}
